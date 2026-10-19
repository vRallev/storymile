package software.ralf.storymile

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.robot
import software.ralf.app.platform.robot.waitUntilCatching
import software.ralf.storymile.player.Player

class PlayerAudioUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `both layouts follow audio snapshots and forward transport commands`() {
    uiTestRule.runRobotTest(windowSize = DpSize(1440.dp, 900.dp)) {
      val initial =
        Player.State(
          title = "Test episode 1",
          positionMillis = 22_500,
          durationMillis = 125_000,
          isReady = true,
        )
      robot<PlayerRobot> { publish(initial) }
      seeAudioState(expanded = false, positionSeconds = 22, playing = false, speed = 1f)

      runOnUiThread { onNodeWithTag("playback-bar-play-pause").performClick() }
      onNodeWithTag("playback-bar-seek").performSemanticsAction(SemanticsActions.SetProgress) {
        it(50f)
      }
      runOnUiThread { onNodeWithTag("playback-bar-speed").performClick() }
      runOnUiThread { onNodeWithTag("playback-option-speed-1.5").performClick() }
      robot<PlayerRobot> { seeCommands(1, listOf(50_000), listOf(1.5f)) }
      seeAudioState(expanded = false, positionSeconds = 22, playing = false, speed = 1f)

      val playing = initial.copy(positionMillis = 30_000, isPlaying = true, speed = 1.5f)
      robot<PlayerRobot> { publish(playing) }
      seeAudioState(expanded = false, positionSeconds = 30, playing = true, speed = 1.5f)

      composeRobot<AppShellRobot> { openPlayback() }
      seeAudioState(expanded = true, positionSeconds = 30, playing = true, speed = 1.5f)
      runOnUiThread { onNodeWithTag("playback-play-pause").performClick() }
      onNodeWithTag("playback-seek").performSemanticsAction(SemanticsActions.SetProgress) {
        it(60f)
      }
      runOnUiThread { onNodeWithTag("playback-back").performClick() }
      runOnUiThread { onNodeWithTag("playback-forward").performClick() }
      runOnUiThread { onNodeWithTag("playback-speed").performScrollTo().performClick() }
      runOnUiThread { onNodeWithTag("playback-option-speed-2.0").performClick() }
      robot<PlayerRobot> {
        seeCommands(2, listOf(50_000, 60_000, 15_000, 60_000), listOf(1.5f, 2f))
        publish(playing.copy(positionMillis = 60_000, isPlaying = false, speed = 2f))
      }
      seeAudioState(expanded = true, positionSeconds = 60, playing = false, speed = 2f)

      composeRobot<AppShellRobot> { collapsePlayback() }
      seeAudioState(expanded = false, positionSeconds = 60, playing = false, speed = 2f)
    }
  }

  private fun ComposeUiTest.seeAudioState(
    expanded: Boolean,
    positionSeconds: Int,
    playing: Boolean,
    speed: Float,
  ) {
    val suffix = if (expanded) "expanded" else "collapsed"
    val prefix = if (expanded) "playback" else "playback-bar"
    val position = "${positionSeconds / 60}:${(positionSeconds % 60).toString().padStart(2, '0')}"
    waitUntilCatching("player follows the audio snapshot", timeout = 3.seconds) {
      onNodeWithTag("playback-title-$suffix", useUnmergedTree = true)
        .assertTextEquals("Test episode 1")
      onNodeWithTag("playback-position-$suffix", useUnmergedTree = true).assertTextEquals(position)
      onNodeWithTag("playback-duration-$suffix", useUnmergedTree = true).assertTextEquals("2:05")
      onNodeWithTag("$prefix-play-pause")
        .assertContentDescriptionEquals(if (playing) "Pause" else "Play")
      onNodeWithTag("$prefix-speed").assertTextContains("${speed}x")
      val progress =
        onNodeWithTag("$prefix-seek")
          .fetchSemanticsNode()
          .config[SemanticsProperties.ProgressBarRangeInfo]
      assertThat(progress).isEqualTo(ProgressBarRangeInfo(positionSeconds.toFloat(), 0f..125f))
    }
  }
}
