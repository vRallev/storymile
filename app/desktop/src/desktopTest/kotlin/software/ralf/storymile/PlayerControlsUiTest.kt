package software.ralf.storymile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
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
        runOnUiThread { onNodeWithTag("playback-bar-play-pause").performTouchInput { click() } }
        onNodeWithTag("playback-bar-play-pause").assertContentDescriptionEquals("Play")
        onNodeWithTag("playback-screen").assertDoesNotExist()

        composeRobot<AppShellRobot> { openPlayback() }
        onNodeWithTag("playback-play-pause").assertContentDescriptionEquals("Play")
        onNodeWithTag("playback-seek").performSemanticsAction(SemanticsActions.SetProgress) {
          it(1080f)
        }
        seePlayerText("18:00")

        runOnUiThread { onNodeWithTag("playback-back").performClick() }
        seePlayerText("17:45")
        runOnUiThread { onNodeWithTag("playback-forward").performClick() }
        seePlayerText("18:15")
        onNodeWithTag("playback-bookmark").assertIsNotEnabled()
        onNodeWithTag("playback-download").assertIsNotEnabled()

        composeRobot<AppShellRobot> { collapsePlayback() }
        onNodeWithTag("playback-bar-play-pause").assertContentDescriptionEquals("Play")
        composeRobot<AppShellRobot> { openPlayback() }
        seePlayerText("18:15")
      }
    }
  }

  @Test
  fun `player options show episode details and keep speed across queue changes`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DpSize(1440.dp, 900.dp)).forEach {
      size ->
      uiTestRule.runRobotTest(windowSize = size) {
        composeRobot<AppShellRobot> { openPlayback() }
        runOnUiThread { onNodeWithTag("playback-speed").performScrollTo().performClick() }
        runOnUiThread { onNodeWithTag("playback-option-speed-1.5").performClick() }
        onNodeWithTag("playback-speed").assertTextContains("1.5x")

        onNodeWithTag("playback-sleep").assertIsNotEnabled()
        onNodeWithTag("playback-chapters").assertIsNotEnabled()
        runOnUiThread { onNodeWithTag("playback-more").performClick() }
        onNode(hasText("Show notes for The art of slowing down.")).assertIsDisplayed()
        runOnUiThread { onNode(hasText("Close")).performClick() }

        runOnUiThread { onNodeWithTag("playback-queue").performClick() }
        runOnUiThread { onNodeWithTag("playback-option-episode-1").performClick() }
        runOnUiThread { onNodeWithTag("playback-seek").performScrollTo() }
        seePlayerText("Making room for what matters")
        seePlayerText("0:00")
        onNodeWithTag("playback-play-pause").assertContentDescriptionEquals("Pause")

        runOnUiThread { onNodeWithTag("playback-speed").performScrollTo() }
        onNodeWithTag("playback-speed").assertTextContains("1.5x")
        composeRobot<AppShellRobot> { collapsePlayback() }
        onNodeWithTag("playback-bar-play-pause").assertContentDescriptionEquals("Pause")
        composeRobot<AppShellRobot> { openPlayback() }
        runOnUiThread { onNodeWithTag("playback-speed").performScrollTo() }
        onNodeWithTag("playback-speed").assertTextContains("1.5x")
      }
    }
  }

  private fun ComposeUiTest.seePlayerText(text: String) {
    val node =
      onNode(
        hasText(text) and hasAnyAncestor(hasTestTag("playback-screen")),
        useUnmergedTree = true,
      )
    waitUntilCatching("player shows $text", timeout = 3.seconds) { node.assertExists() }
    runOnUiThread { node.performScrollTo() }
    node.assertIsDisplayed()
  }
}
