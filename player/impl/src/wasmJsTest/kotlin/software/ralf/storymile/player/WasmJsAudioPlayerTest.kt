@file:OptIn(ExperimentalWasmJsInterop::class)

package software.ralf.storymile.player

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.AfterTest
import kotlin.test.Test

class WasmJsAudioPlayerTest {
  private val browser = installTestBrowserAudio()
  private val player: AudioPlayer = WasmJsAudioPlayer()

  @AfterTest
  fun tearDown() {
    player.close()
    browser.restore()
  }

  @Test
  fun reportsTheMediaClockAndActualPlayback() {
    player.load("episode-1.mp3")
    player.play()

    assertThat(browser.playCount).isEqualTo(1)
    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(player.state.value.isReady).isFalse()

    browser.prepare(123.5)
    browser.startPlaying()
    browser.advance(20.25)

    assertThat(player.state.value.durationMillis).isEqualTo(123_500)
    assertThat(player.state.value.positionMillis).isEqualTo(20_250)
    assertThat(player.state.value.isPlaying).isTrue()
    assertThat(player.state.value.playWhenReady).isTrue()
    assertThat(player.state.value.isReady).isTrue()

    player.pause()

    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(player.state.value.playWhenReady).isFalse()
    assertThat(player.state.value.positionMillis).isEqualTo(20_250)
  }

  @Test
  fun keepsPlaybackRequestedWhileBufferingButClearsItForNativePause() {
    player.load("episode-1.mp3")
    browser.prepare(30.0)
    player.play()
    browser.startPlaying()
    browser.waitForData()

    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(player.state.value.playWhenReady).isTrue()

    browser.nativePause()

    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(player.state.value.playWhenReady).isFalse()
  }

  @Test
  fun defersSeekingUntilMetadataAndClampsToTheDuration() {
    player.load("episode-1.mp3")
    player.seekTo(50_000)
    browser.prepare(30.0)

    assertThat(player.state.value.positionMillis).isEqualTo(30_000)

    player.seekTo(-5_000)

    assertThat(player.state.value.positionMillis).isEqualTo(0)
  }

  @Test
  fun ignoresRejectedPlayRequestsAfterSourceChangesAndPause() {
    player.load("episode-1.mp3")
    player.play()
    player.load("episode-2.mp3")
    browser.prepare(40.0)
    player.play()
    browser.startPlaying()
    browser.rejectPlay(0, "Previous episode was replaced")

    assertThat(player.state.value.source).isEqualTo("episode-2.mp3")
    assertThat(player.state.value.isPlaying).isTrue()
    assertThat(player.state.value.error).isNull()

    player.pause()
    browser.rejectPlay(1, "Playback was paused")

    assertThat(player.state.value.error).isNull()

    player.play()
    browser.rejectPlay(2, "Playback is not allowed")

    assertThat(player.state.value.error).isEqualTo("Playback is not allowed")
    assertThat(player.state.value.isPlaying).isFalse()
    assertThat(player.state.value.playWhenReady).isFalse()
  }

  @Test
  fun preservesPitchAndSpeedAcrossEpisodes() {
    assertThat(browser.speed).isEqualTo(1.0)
    assertThat(browser.preservesPitch).isTrue()

    player.load("episode-1.mp3")
    player.setSpeed(1.5f)
    player.load("episode-2.mp3")

    assertThat(browser.speed).isEqualTo(1.5)
    assertThat(browser.preservesPitch).isTrue()
    assertThat(browser.elementCount).isEqualTo(1)
  }

  @Test
  fun onlyReportsTheCurrentEpisodesEndAndReleasesCallbacks() {
    player.load("episode-1.mp3")
    browser.prepare(30.0)
    player.play()
    browser.startPlaying()
    browser.finish()

    assertThat(player.state.value.hasEnded).isTrue()
    assertThat(player.state.value.isPlaying).isFalse()

    player.load("episode-2.mp3")
    browser.prepare(40.0)
    browser.dispatchEnded()

    assertThat(player.state.value.hasEnded).isFalse()

    player.close()
    browser.advance(8.0)
    browser.rejectPlay(0, "Disposed player")

    assertThat(browser.listenerCount).isEqualTo(0)
    assertThat(player.state.value).isEqualTo(AudioPlayer.State())
  }
}
