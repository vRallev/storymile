package software.ralf.storymile.player

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopAudioPlayerTest {
  @Test
  fun `preparing does not open the audio device and playing waits for preparation`() = runTest {
    val decoded = CompletableDeferred<PcmAudio>()
    val output = TestOutput()
    val player: AudioPlayer = DesktopAudioPlayer(backgroundScope, { decoded.await() }, { output })

    player.load("episode")
    runCurrent()
    assertThat(player.state.value.isReady).isFalse()
    assertThat(output.opened).isFalse()

    player.play()
    runCurrent()
    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(player.state.value.playWhenReady).isTrue()
    decoded.complete(tone())
    runCurrent()
    assertThat(player.state.value.isPlaying).isTrue()
    assertThat(output.opened).isTrue()
    player.close()
  }

  @Test
  fun `progress follows heard frames and pause discards queued audio`() = runTest {
    val output = TestOutput()
    val player: AudioPlayer = DesktopAudioPlayer(backgroundScope, { tone() }, { output })
    player.load("episode")
    player.play()
    runCurrent()

    // Rendering ahead into the device buffer must not move the displayed position.
    assertThat(output.queuedFrames > 0).isTrue()
    assertThat(player.state.value.positionMillis).isEqualTo(0L)
    output.consume(2_400)
    advanceTimeBy(10)
    runCurrent()
    assertThat(player.state.value.positionMillis).isEqualTo(50L)

    player.pause()
    runCurrent()
    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(player.state.value.playWhenReady).isFalse()
    assertThat(output.queuedFrames).isEqualTo(0)
    output.consume(2_400)
    advanceTimeBy(100)
    runCurrent()
    assertThat(player.state.value.positionMillis).isEqualTo(50L)
    player.close()
  }

  @Test
  fun `seeking and speed changes discard old audio and preserve the current position`() = runTest {
    val output = TestOutput()
    val player: AudioPlayer = DesktopAudioPlayer(backgroundScope, { tone() }, { output })
    player.load("episode")
    player.play()
    runCurrent()
    output.consume(2_400)

    player.setSpeed(2f)
    runCurrent()
    assertThat(player.state.value.positionMillis).isEqualTo(50L)
    output.consume(2_400)
    advanceTimeBy(10)
    runCurrent()
    assertThat(player.state.value.positionMillis).isEqualTo(150L)

    player.seekTo(1_000)
    runCurrent()
    assertThat(player.state.value.positionMillis).isEqualTo(1_000L)
    output.consume(2_400)
    advanceTimeBy(10)
    runCurrent()
    assertThat(player.state.value.positionMillis).isEqualTo(1_100L)
    player.close()
  }

  @Test
  fun `replacement prepares paused keeps speed and ignores a late decoder`() = runTest {
    val oldAudio = CompletableDeferred<PcmAudio>()
    val outputs = mutableListOf<TestOutput>()
    val player: AudioPlayer =
      DesktopAudioPlayer(
        backgroundScope,
        { source ->
          if (source == "old") {
            withContext(NonCancellable) { oldAudio.await() }
          } else {
            tone()
          }
        },
        { TestOutput().also { outputs += it } },
      )
    player.load("old")
    player.play()
    runCurrent()
    player.setSpeed(2f)
    player.load("new")
    runCurrent()
    assertThat(player.state.value.source).isEqualTo("new")
    assertThat(player.state.value.isReady).isTrue()
    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(outputs.isEmpty()).isTrue()
    oldAudio.complete(tone(seconds = 1))
    runCurrent()
    assertThat(player.state.value.durationMillis).isEqualTo(2_000L)

    player.play()
    runCurrent()
    outputs.single().consume(2_400)
    advanceTimeBy(10)
    runCurrent()
    assertThat(player.state.value.positionMillis).isEqualTo(100L)
    player.close()
    assertThat(outputs.single().closed).isTrue()
    assertThat(player.state.value.isPlaying).isFalse()
    player.play()
    player.load("after close")
    runCurrent()
    assertThat(player.state.value.source).isEqualTo("new")
    assertThat(player.state.value.isPlaying).isFalse()
  }

  @Test
  fun `output failures become player errors and release the audio device`() = runTest {
    val output = TestOutput(failOpening = true)
    val player: AudioPlayer = DesktopAudioPlayer(backgroundScope, { tone() }, { output })
    player.load("episode")
    player.play()
    runCurrent()
    assertThat(player.state.value.error).isNotNull()
    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(output.closed).isTrue()
    player.close()
  }

  @Test
  fun `speed changes duration while preserving pitch and end waits for the device`() = runTest {
    for (speed in listOf(0.75f, 1f, 1.2f, 1.5f, 2f)) {
      val output = TestOutput()
      val player: AudioPlayer = DesktopAudioPlayer(backgroundScope, { tone() }, { output })
      player.setSpeed(speed)
      player.load("episode")
      player.play()
      runCurrent()
      repeat(400) {
        output.consume(480)
        advanceTimeBy(10)
        runCurrent()
      }
      assertThat(player.state.value.hasEnded).isTrue()
      assertThat(player.state.value.isPlaying).isFalse()
      assertThat(player.state.value.playWhenReady).isFalse()
      assertThat(player.state.value.positionMillis).isEqualTo(2_000L)

      val bytes = output.written.toByteArray()
      val samples =
        ShortArray(bytes.size / 2) { index ->
          ((bytes[index * 2].toInt() and 0xff) or (bytes[index * 2 + 1].toInt() shl 8)).toShort()
        }
      assertThat(abs(samples.size - 96_000 / speed)).isLessThan(1_920f)
      // Measure the steady part, excluding the time-stretcher's leading and trailing edges.
      val start = 4_800
      val end = samples.size - 4_800
      val crossings = (start + 1 until end).count { samples[it - 1] <= 0 && samples[it] > 0 }
      val frequency = crossings * 48_000f / (end - start)
      assertThat(abs(frequency - 440)).isLessThan(3f)
      player.close()
    }
  }

  private fun tone(seconds: Int = 2): PcmAudio =
    PcmAudio(
      ShortArray(48_000 * seconds) { (sin(2 * PI * 440 * it / 48_000) * 16_000).toInt().toShort() },
      sampleRate = 48_000,
      channels = 1,
    )

  private class TestOutput(private val failOpening: Boolean = false) : AudioOutput {
    override var playedFrames = 0L
      private set

    override val availableBytes: Int
      get() = (4_800 - queuedFrames) * frameBytes

    var queuedFrames = 0
      private set

    var opened = false
      private set

    var closed = false
      private set

    val written = ByteArrayOutputStream()
    private var playing = false
    private var frameBytes = 2

    override fun open(audio: PcmAudio) {
      check(!failOpening) { "No audio output device." }
      frameBytes = audio.channels * 2
      opened = true
    }

    override fun start() {
      playing = true
    }

    override fun stop() {
      playing = false
    }

    override fun flush() {
      queuedFrames = 0
    }

    override fun write(bytes: ByteArray, offset: Int, count: Int): Int {
      written.write(bytes, offset, count)
      queuedFrames += count / frameBytes
      return count
    }

    override fun close() {
      closed = true
      playing = false
      queuedFrames = 0
    }

    fun consume(frames: Int) {
      if (playing) {
        val consumed = frames.coerceAtMost(queuedFrames)
        queuedFrames -= consumed
        playedFrames += consumed
      }
    }
  }
}
