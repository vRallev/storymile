package software.ralf.storymile.player

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import software.ralf.storymile.user.UserScope
import sonic.Sonic

@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class DesktopAudioPlayer
internal constructor(
  coroutineScope: CoroutineScope,
  private val decode: suspend (String) -> PcmAudio,
  private val createOutput: () -> AudioOutput,
) : AudioPlayer() {
  override val state: StateFlow<State>
    field = MutableStateFlow(State())

  private val commands = Channel<Command>(Channel.UNLIMITED)
  private val lock = Any()
  private var closed = false
  private var generation = 0L
  private var preparation: Job? = null
  private var session: Session? = null
  private var playRequested = false
  private var speed = 1f
  private val job = coroutineScope.launch {
    try {
      while (true) {
        val command =
          if (synchronized(lock) { playRequested && session != null }) {
            withTimeoutOrNull(10) { commands.receive() }
          } else {
            commands.receive()
          }
        synchronized(lock) {
          ensureActive()
          if (!closed) {
            runCatching {
              if (command != null) {
                handle(command, this)
              }
              while (true) {
                val next = commands.tryReceive().getOrNull() ?: break
                ensureActive()
                handle(next, this)
              }
              ensureActive()
              if (playRequested) {
                session?.render()
              }
            }
              .onFailure { exception ->
                if (exception is CancellationException) {
                  throw exception
                }
                fail(exception)
              }
          }
        }
      }
    } finally {
      synchronized(lock) {
        closed = true
        preparation?.cancel()
        session?.close()
        session = null
        state.update { it.copy(isPlaying = false, playWhenReady = false) }
      }
      commands.close()
    }
  }

  @Inject
  constructor(
    @ForScope(UserScope::class) coroutineScope: CoroutineScope,
  ) : this(coroutineScope, { Mp3Decoder.decode(it) }, { JavaSoundOutput() })

  override fun load(source: String) {
    send(Command.Load(source))
  }

  override fun play() {
    send(Command.Play)
  }

  override fun pause() {
    send(Command.Pause)
  }

  override fun seekTo(positionMillis: Long) {
    send(Command.Seek(positionMillis.coerceAtLeast(0)))
  }

  override fun setSpeed(speed: Float) {
    require(speed.isFinite() && speed > 0) { "Playback speed must be positive." }
    send(Command.Speed(speed))
  }

  override fun close() {
    synchronized(lock) {
      closed = true
      session?.close()
      session = null
      state.update { it.copy(isPlaying = false, playWhenReady = false) }
    }
    job.cancel()
  }

  private fun send(command: Command) {
    synchronized(lock) {
      if (!closed) {
        commands.trySend(command)
      }
    }
  }

  private fun handle(command: Command, coroutineScope: CoroutineScope) {
    when (command) {
      is Command.Load -> prepare(command.source, coroutineScope)
      is Command.Prepared -> prepared(command)
      is Command.Failed -> {
        if (command.generation == generation) {
          fail(command.exception)
        }
      }
      Command.Play -> requestPlayback()
      Command.Pause -> {
        playRequested = false
        session?.let { it.reset(it.positionMillis()) }
        state.update { it.copy(isPlaying = false, playWhenReady = false) }
      }
      is Command.Seek -> seek(command.positionMillis)
      is Command.Speed -> {
        val position = session?.positionMillis() ?: state.value.positionMillis
        speed = command.speed
        session?.reset(position)
      }
    }
  }

  private fun prepare(source: String, coroutineScope: CoroutineScope) {
    generation++
    preparation?.cancel()
    session?.close()
    session = null
    playRequested = false
    state.value = State(source = source)
    val requestedGeneration = generation
    preparation = coroutineScope.launch {
      runCatching {
        val audio = decode(source)
        ensureActive()
        commands.send(Command.Prepared(requestedGeneration, audio))
      }
        .onFailure { exception ->
          if (exception is CancellationException) {
            throw exception
          }
          commands.send(Command.Failed(requestedGeneration, exception))
        }
    }
  }

  private fun prepared(command: Command.Prepared) {
    if (command.generation == generation) {
      val position = state.value.positionMillis.coerceAtMost(command.audio.durationMillis)
      session = Session(command.audio).apply { reset(position) }
      state.update {
        it.copy(
          positionMillis = position,
          durationMillis = command.audio.durationMillis,
          isReady = true,
        )
      }
    }
  }

  private fun requestPlayback() {
    if (state.value.source != null && state.value.error == null && !state.value.hasEnded) {
      playRequested = true
      state.update { it.copy(playWhenReady = true) }
    }
  }

  private fun seek(positionMillis: Long) {
    val position =
      session?.let { positionMillis.coerceAtMost(it.audio.durationMillis) } ?: positionMillis
    session?.reset(position)
    state.update { it.copy(positionMillis = position, hasEnded = false) }
  }

  private fun fail(exception: Throwable) {
    playRequested = false
    session?.close()
    session = null
    state.update {
      it.copy(
        isPlaying = false,
        playWhenReady = false,
        isReady = false,
        error = exception.message ?: "Audio playback failed.",
      )
    }
  }

  private inner class Session(val audio: PcmAudio) {
    private var output: AudioOutput? = null
    private var sonic = Sonic(audio.sampleRate, audio.channels)
    private var segmentPositionMillis = 0L
    private var segmentOutputFrame = 0L
    private var inputFrame = 0
    private var writtenFrames = 0L
    private var inputEnded = false
    private var pending = ByteArray(0)
    private var pendingOffset = 0

    fun positionMillis(): Long {
      val consumed = (output?.playedFrames ?: 0) - segmentOutputFrame
      return (segmentPositionMillis + (consumed * speed * 1_000 / audio.sampleRate).toLong())
        .coerceAtMost(audio.durationMillis)
    }

    fun reset(positionMillis: Long) {
      output?.stop()
      output?.flush()
      segmentPositionMillis = positionMillis
      segmentOutputFrame = output?.playedFrames ?: 0
      inputFrame =
        (positionMillis * audio.sampleRate / 1_000).toInt().coerceAtMost(audio.frameCount)
      writtenFrames = 0
      inputEnded = false
      pending = ByteArray(0)
      pendingOffset = 0
      sonic =
        Sonic(audio.sampleRate, audio.channels).apply { setSpeed(this@DesktopAudioPlayer.speed) }
      state.update { it.copy(positionMillis = positionMillis) }
    }

    fun render() {
      val device =
        output
          ?: createOutput().also {
            output = it
            it.open(audio)
            segmentOutputFrame = it.playedFrames
          }
      device.start()
      val frameBytes = audio.channels * 2
      repeat(8) {
        val available = device.availableBytes / frameBytes * frameBytes
        if (available == 0) {
          return@repeat
        }
        if (pendingOffset == pending.size) {
          pending = nextSamples()
          pendingOffset = 0
        }
        val count = min(available, pending.size - pendingOffset)
        if (count > 0) {
          val written = device.write(pending, pendingOffset, count)
          pendingOffset += written
          writtenFrames += written / frameBytes
        }
      }
      val hasEnded =
        inputEnded &&
          pendingOffset == pending.size &&
          sonic.samplesAvailable() == 0 &&
          device.playedFrames - segmentOutputFrame >= writtenFrames
      if (hasEnded) {
        playRequested = false
        device.stop()
      }
      state.update {
        it.copy(
          positionMillis = if (hasEnded) audio.durationMillis else positionMillis(),
          isPlaying = !hasEnded,
          playWhenReady = !hasEnded,
          hasEnded = hasEnded,
        )
      }
    }

    private fun nextSamples(): ByteArray {
      while (sonic.samplesAvailable() == 0 && !inputEnded) {
        val endFrame = min(inputFrame + 1_024, audio.frameCount)
        sonic.writeShortToStream(
          audio.samples.copyOfRange(inputFrame * audio.channels, endFrame * audio.channels),
          endFrame - inputFrame,
        )
        inputFrame = endFrame
        if (inputFrame == audio.frameCount) {
          sonic.flushStream()
          inputEnded = true
        }
      }
      val bytes = ByteArray(min(sonic.samplesAvailable(), 2_048) * audio.channels * 2)
      sonic.readBytesFromStream(bytes, bytes.size)
      return bytes
    }

    fun close() {
      output?.close()
      output = null
    }
  }

  private sealed interface Command {
    data class Load(val source: String) : Command

    data class Prepared(val generation: Long, val audio: PcmAudio) : Command

    data class Failed(val generation: Long, val exception: Throwable) : Command

    data class Seek(val positionMillis: Long) : Command

    data class Speed(val speed: Float) : Command

    data object Play : Command

    data object Pause : Command
  }
}
