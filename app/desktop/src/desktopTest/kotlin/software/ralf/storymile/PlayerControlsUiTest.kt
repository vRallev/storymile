package software.ralf.storymile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching

class PlayerControlsUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `player controls share state across collapsed and expanded layouts`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DpSize(1440.dp, 900.dp)).forEach {
      size ->
      uiTestRule.runRobotTest(windowSize = size) {
        val playPause = onNodeWithTag("playback-bar-play-pause")
        playPause.assertContentDescriptionEquals("Play")
        runOnUiThread { playPause.performClick() }
        waitUntilCatching("bar shows playing state") {
          playPause.assertContentDescriptionEquals("Pause")
        }
        onNodeWithTag("playback-screen").assertDoesNotExist()

        composeRobot<AppShellRobot> { openPlayback() }
        onNodeWithTag("playback-play-pause").assertContentDescriptionEquals("Pause")
        val seek = onNodeWithTag("playback-seek")
        waitUntilCatching("queue audio is ready") { seek.assertIsEnabled() }
        seek.performSemanticsAction(SemanticsActions.SetProgress) { it(1080f) }
        seePosition("18:00")

        runOnUiThread { onNodeWithTag("playback-back").performClick() }
        seePosition("17:45")
        runOnUiThread { onNodeWithTag("playback-forward").performClick() }
        seePosition("18:15")

        composeRobot<AppShellRobot> { collapsePlayback() }
        runOnUiThread { playPause.performClick() }
        waitUntilCatching("bar shows paused state") {
          playPause.assertContentDescriptionEquals("Play")
        }
        composeRobot<AppShellRobot> { openPlayback() }
        onNodeWithTag("playback-play-pause").assertContentDescriptionEquals("Play")
        seePosition("18:15")
      }
    }
  }

  @Test
  fun `selecting a queued episode preserves playback speed`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DpSize(1440.dp, 900.dp)).forEach {
      size ->
      uiTestRule.runRobotTest(windowSize = size) {
        composeRobot<AppShellRobot> { openPlayback() }
        runOnUiThread { onNodeWithTag("playback-speed").performScrollTo().performClick() }
        runOnUiThread { onNodeWithTag("playback-option-speed-1.5").performClick() }
        onNodeWithTag("playback-speed").assertTextContains("1.5x")

        runOnUiThread { onNodeWithTag("playback-queue").performScrollTo().performClick() }
        val second = onNodeWithTag("playback-option-episode-0")
        val third = onNodeWithTag("playback-option-episode-1")
        second.assertTextContains("The sound of a quieter life")
        third.assertTextContains("Making room for what matters")
        runOnUiThread { third.performClick() }

        waitUntilCatching("selected episode appears") {
          onNodeWithTag("playback-title-expanded", useUnmergedTree = true)
            .assertTextEquals("Making room for what matters")
          onNodeWithTag("playback-play-pause").assertContentDescriptionEquals("Pause")
        }
        seePosition("0:00")
        onNodeWithTag("playback-speed").assertTextContains("1.5x")
        composeRobot<AppShellRobot> { collapsePlayback() }
        onNodeWithTag("playback-bar-play-pause").assertContentDescriptionEquals("Pause")
      }
    }
  }

  private fun ComposeUiTest.seePosition(text: String) {
    waitUntilCatching("player position is $text", timeout = 3.seconds) {
      onNodeWithTag("playback-position-expanded", useUnmergedTree = true).assertTextEquals(text)
    }
  }
}
