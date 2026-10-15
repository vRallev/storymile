@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThan
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching

class PlayerTransitionInputUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `short wide windows show a usable speed control without a collapsed counterpart`() {
    uiTestRule.runRobotTest(windowSize = DpSize(1440.dp, 480.dp), darkTheme = false) {
      onNodeWithTag("playback-bar-speed").assertDoesNotExist()
      composeRobot<AppShellRobot> { openPlayback() }
      runOnUiThread { onNodeWithTag("playback-speed").performScrollTo() }
      val pixels =
        onNodeWithTag("playback-speed-label-expanded", useUnmergedTree = true)
          .assertIsDisplayed()
          .captureToImage()
          .toPixelMap()
      val minimumRed =
        (0 until pixels.height).minOf { y ->
          (0 until pixels.width).minOf { x -> pixels[x, y].red }
        }
      assertThat(minimumRed, "The unpaired Speed label must draw its text").isLessThan(0.6f)
      runOnUiThread { onNodeWithTag("playback-speed").performClick() }
      runOnUiThread { onNodeWithTag("playback-option-speed-1.5").performClick() }
      waitUntilCatching("short wide player accepts speed", timeout = 3.seconds) {
        onNodeWithTag("playback-speed-value-expanded", useUnmergedTree = true)
          .assertTextEquals("1.5x")
      }
    }
  }

  @Test
  fun `seek and speed ignore endpoint input during motion and resume after settling`() {
    uiTestRule.runRobotTest(windowSize = DpSize(1440.dp, 900.dp)) {
      onNodeWithTag("playback-position-collapsed", useUnmergedTree = true).assertTextEquals("12:34")
      val collapsedTop = onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top
      mainClock.autoAdvance = false
      try {
        composeRobot<AppShellRobot> { openPlayback() }
        advanceToMiddle(collapsedTop)
        listOf("playback-bar-seek", "playback-seek").forEach { seek ->
          onNodeWithTag(seek, useUnmergedTree = true).performSemanticsAction(
            SemanticsActions.SetProgress,
          ) {
            it(1080f)
          }
        }
        listOf("playback-bar-speed", "playback-speed").forEach { speed ->
          runOnUiThread { onNodeWithTag(speed, useUnmergedTree = true).performClick() }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithTag("playback-position-expanded", useUnmergedTree = true)
          .assertTextEquals("12:34")
        onNodeWithTag("playback-option-speed-1.5").assertDoesNotExist()
      } finally {
        mainClock.autoAdvance = true
      }

      waitForIdle()
      onNodeWithTag("playback-position-expanded", useUnmergedTree = true).assertTextEquals("12:34")
      onNodeWithTag("playback-option-speed-1.5").assertDoesNotExist()
      seekAndSetSpeed(expanded = true, position = 1080f, time = "18:00", speed = "1.5")

      composeRobot<AppShellRobot> { collapsePlayback() }
      waitForIdle()
      seekAndSetSpeed(expanded = false, position = 1200f, time = "20:00", speed = "2.0")
    }
  }

  private fun ComposeUiTest.advanceToMiddle(collapsedTop: Float) {
    repeat(60) {
      mainClock.advanceTimeByFrame()
      val top = onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top
      if (1f - top / collapsedTop in 0.3f..0.7f) {
        return
      }
    }
    error("Player did not render an intermediate animation frame")
  }

  private fun ComposeUiTest.seekAndSetSpeed(
    expanded: Boolean,
    position: Float,
    time: String,
    speed: String,
  ) {
    val prefix = if (expanded) "playback" else "playback-bar"
    val suffix = if (expanded) "expanded" else "collapsed"
    onNodeWithTag("$prefix-seek").performSemanticsAction(SemanticsActions.SetProgress) {
      it(position)
    }
    waitUntilCatching("settled player accepts seek", timeout = 3.seconds) {
      onNodeWithTag("playback-position-$suffix", useUnmergedTree = true).assertTextEquals(time)
    }
    runOnUiThread { onNodeWithTag("$prefix-speed").performClick() }
    runOnUiThread { onNodeWithTag("playback-option-speed-$speed").performClick() }
    waitUntilCatching("settled player accepts speed", timeout = 3.seconds) {
      onNodeWithTag("playback-speed-value-$suffix", useUnmergedTree = true)
        .assertTextEquals("${speed}x")
    }
  }
}
