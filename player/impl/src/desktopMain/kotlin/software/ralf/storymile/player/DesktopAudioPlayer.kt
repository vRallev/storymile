package software.ralf.storymile.player

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.nio.ByteBuffer
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

  private val commands = Channel<Command>(Channel.UNLIMITED) { it.release() }
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
          if (!closed) {
            try {
              if (command != null) {
                handle(command, this)
              }
              while (true) {
                ensureActive()
                val next = commands.tryReceive().getOrNull() ?: break
                handle(next, this)
              }
              ensureActive()
              if (playRequested) {
                session?.render()
              }
            } catch (exception: Throwable) {
              if (exception is CancellationException) {
                throw exception
              }
              fail(exception)
            }
          } else {
            command?.release()
          }
        }
      }
    } finally {
      try {
        synchronized(lock) {
          closed = true
          releaseSource()
          state.update { it.copy(isPlaying = false, playWhenReady = false) }
        }
      } finally {
        commands.cancel()
      }
    }
  }

  @Inject
  constructor(
    @ForScope(UserScope::class) coroutineScope: CoroutineScope,
  ) : this(coroutineScope, { Mp3Decoder.decode(it) }, { JavaSoundOutput() })

  override fun load(source: String) {
    send(Command.SetSource(source))
  }

  override fun clear() {
    send(Command.SetSource(null))
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
    try {
      synchronized(lock) {
        closed = true
        releaseSource()
        state.update { it.copy(isPlaying = false, playWhenReady = false) }
      }
    } finally {
      job.cancel()
    }
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
      is Command.SetSource -> prepare(command.source, coroutineScope)
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

  private fun prepare(source: String?, coroutineScope: CoroutineScope) {
    releaseSource()
    state.value = State(source = source)
    if (source == null) {
      return
    }
    val requestedGeneration = generation
    preparation = coroutineScope.launch {
      try {
        val audio = decode(source)
        if (!isActive || commands.trySend(Command.Prepared(requestedGeneration, audio)).isFailure) {
          audio.close()
        }
      } catch (exception: Throwable) {
        if (exception is CancellationException) {
          throw exception
        }
        commands.send(Command.Failed(requestedGeneration, exception))
      }
    }
  }

  private fun releaseSource() {
    generation++
    preparation?.cancel()
    preparation = null
    playRequested = false
    val previous = session
    session = null
    previous?.close()
  }

  private fun prepared(command: Command.Prepared) {
    if (command.generation == generation) {
      val position = state.value.positionMillis.coerceAtMost(command.audio.durationMillis)
      val prepared = Session(command.audio)
      session = prepared
      prepared.reset(position)
      state.update {
        it.copy(
          positionMillis = position,
          durationMillis = command.audio.durationMillis,
          isReady = true,
        )
      }
    } else {
      command.release()
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
    releaseSource()
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
    private val frameBytes = audio.channels * Short.SIZE_BYTES
    private val input = ByteBuffer.allocate(1_024 * frameBytes)
    private val pending = ByteArray(2_048 * frameBytes)
    private var pendingSize = 0
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
      pendingSize = 0
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
      for (attempt in 0 until 8) {
        val available = device.availableBytes / frameBytes * frameBytes
        if (available > 0 && pendingOffset == pendingSize) {
          pendingSize = nextSamples()
          pendingOffset = 0
        }
        val count = min(available, pendingSize - pendingOffset)
        if (count == 0) {
          break
        }
        val written = device.write(pending, pendingOffset, count)
        pendingOffset += written
        writtenFrames += written / frameBytes
      }
      val hasEnded =
        inputEnded &&
          pendingOffset == pendingSize &&
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

    private fun nextSamples(): Int {
      while (sonic.samplesAvailable() == 0 && !inputEnded) {
        val frames = min(input.capacity() / frameBytes, audio.frameCount - inputFrame)
        input.clear().limit(frames * frameBytes)
        audio.readFrames(startFrame = inputFrame, destination = input)
        sonic.writeBytesToStream(input.array(), input.position())
        inputFrame += frames
        if (inputFrame == audio.frameCount) {
          sonic.flushStream()
          inputEnded = true
        }
      }
      return sonic.readBytesFromStream(pending, pending.size)
    }

    fun close() {
      try {
        output?.close()
      } finally {
        output = null
        audio.close()
      }
    }
  }

  private sealed interface Command {
    fun release() {
      if (this is Prepared) {
        audio.close()
      }
    }

    data class SetSource(val source: String?) : Command

    data class Prepared(val generation: Long, val audio: PcmAudio) : Command

    data class Failed(val generation: Long, val exception: Throwable) : Command

    data class Seek(val positionMillis: Long) : Command

    data class Speed(val speed: Float) : Command

    data object Play : Command

    data object Pause : Command
  }
}
