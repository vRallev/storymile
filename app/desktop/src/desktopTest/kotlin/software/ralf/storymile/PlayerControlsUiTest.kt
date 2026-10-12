@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
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
        onNodeWithTag("playback-bar-play-pause").performTouchInput { click() }
        onNodeWithTag("playback-bar-play-pause").assertContentDescriptionEquals("Play")
        onNodeWithTag("playback-screen").assertDoesNotExist()

        composeRobot<AppShellRobot> { openPlayback() }
        onNodeWithTag("playback-play-pause").assertContentDescriptionEquals("Play")
        onNodeWithTag("playback-seek").performSemanticsAction(SemanticsActions.SetProgress) {
          it(1080f)
        }
        seePlayerText("18:00")
        assertChapterText("Chapter 4 of 8")

        onNodeWithTag("playback-back").performClick()
        seePlayerText("17:45")
        onNodeWithTag("playback-forward").performClick()
        seePlayerText("18:15")
        onNodeWithTag("playback-bookmark").performScrollTo().performClick()
        onNodeWithTag("playback-download").performClick()

        composeRobot<AppShellRobot> { collapsePlayback() }
        onNodeWithTag("playback-bar-play-pause").assertContentDescriptionEquals("Play")
        composeRobot<AppShellRobot> { openPlayback() }
        onNodeWithTag("playback-bookmark").assertIsOn()
        onNodeWithTag("playback-download").assertIsOn()
        seePlayerText("18:15")
      }
    }
  }

  @Test
  fun `player options select chapters and queued episodes while retaining preferences`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DpSize(1440.dp, 900.dp)).forEach {
      size ->
      uiTestRule.runRobotTest(windowSize = size) {
        composeRobot<AppShellRobot> { openPlayback() }
        onNodeWithTag("playback-speed").performScrollTo().performClick()
        onNodeWithTag("playback-option-speed-1.5").performClick()
        onNodeWithTag("playback-speed").assertTextContains("1.5x")

        onNodeWithTag("playback-sleep").performClick()
        onNodeWithTag("playback-option-sleep-30").performClick()
        onNodeWithTag("playback-sleep").assertTextContains("30 min")

        onNodeWithTag("playback-chapters").performClick()
        onNodeWithTag("playback-option-chapter-4").performScrollTo().performClick()
        assertChapterText("Returning to yourself")
        assertChapterText("Chapter 5 of 8")

        onNodeWithTag("playback-queue").performClick()
        onNodeWithTag("playback-option-episode-1").performClick()
        onNodeWithTag("playback-seek").performScrollTo()
        seePlayerText("Making room for what matters")
        seePlayerText("0:00")
        onNodeWithTag("playback-play-pause").assertContentDescriptionEquals("Pause")

        onNodeWithTag("playback-speed").performScrollTo().assertTextContains("1.5x")
        onNodeWithTag("playback-sleep").assertTextContains("30 min")
        composeRobot<AppShellRobot> { collapsePlayback() }
        onNodeWithTag("playback-bar-play-pause").assertContentDescriptionEquals("Pause")
        composeRobot<AppShellRobot> { openPlayback() }
        onNodeWithTag("playback-speed").performScrollTo().assertTextContains("1.5x")
        onNodeWithTag("playback-sleep").assertTextContains("30 min")
      }
    }
  }

  @Test
  fun `short windows can scroll to the chapter card and the last chapter choice`() {
    listOf(DpSize(360.dp, 480.dp), DpSize(900.dp, 480.dp)).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size) {
        composeRobot<AppShellRobot> { openPlayback() }
        onNodeWithTag("playback-current-chapter")
          .performScrollTo()
          .assertIsDisplayed()
          .performClick()
        onNodeWithTag("playback-option-chapter-7")
          .performScrollTo()
          .assertIsDisplayed()
          .performClick()
        onNodeWithTag("playback-current-chapter").assertIsDisplayed()
        assertChapterText("Closing thoughts")
        assertChapterText("Chapter 8 of 8")
        composeRobot<AppShellRobot> { collapsePlayback() }
        onNodeWithTag("playback-artwork-collapsed", useUnmergedTree = true).assertIsDisplayed()
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
    node.performScrollTo().assertIsDisplayed()
  }

  private fun ComposeUiTest.assertChapterText(text: String) {
    waitUntilCatching("chapter shows $text", timeout = 3.seconds) {
      onNode(
          hasText(text) and hasAnyAncestor(hasTestTag("playback-current-chapter")),
          useUnmergedTree = true,
        )
        .assertExists()
    }
  }
}
