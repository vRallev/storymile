package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot

class PlayerResizeUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `expanded speed remains visible through live window preset changes`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(
      windowSize = DpSize(1440.dp, 900.dp),
      darkTheme = false,
      contentSize = size,
    ) {
      composeRobot<AppShellRobot> { openPlayback() }
      runOnUiThread { onNodeWithTag("playback-speed").performScrollTo() }
      onNodeWithTag("playback-bar-speed", useUnmergedTree = true).assertDoesNotExist()
      assertSpeedDrawn("initial-phone")
      repeat(2) { cycle ->
        listOf(
            DesktopWindowSizes.tablet,
            DpSize(900.dp, 760.dp),
            DpSize(1440.dp, 900.dp),
            DesktopWindowSizes.phone,
          )
          .forEach { next ->
            runOnIdle { size.value = next }
            waitForIdle()
            if (next == DesktopWindowSizes.phone) {
              onNodeWithTag("playback-bar-speed", useUnmergedTree = true).assertDoesNotExist()
              runOnUiThread { onNodeWithTag("playback-speed").performScrollTo() }
            } else {
              onNodeWithTag("playback-bar-speed", useUnmergedTree = true).assertExists()
            }
            assertSpeedDrawn(
              "cycle-$cycle-${next.width.value.toInt()}",
              pairedControls = next != DesktopWindowSizes.phone,
            )
            if (cycle == 0 && next == DesktopWindowSizes.tablet) {
              runOnUiThread { onNodeWithTag("playback-speed").performClick() }
              runOnUiThread { onNodeWithTag("playback-option-speed-1.5").performClick() }
              onNodeWithTag("playback-speed-value-expanded", useUnmergedTree = true)
                .assertTextEquals("1.5x")
            }
          }
      }
    }
  }

  private fun ComposeUiTest.assertSpeedDrawn(step: String, pairedControls: Boolean = false) {
    val screenshot = onRoot().captureToImage()
    val file = File("build/reports/screenshots/player-resize-$step.png")
    file.parentFile.mkdirs()
    ImageIO.write(screenshot.toAwtImage(), "png", file)
    val pixels = screenshot.toPixelMap()
    val parts =
      listOf("speed-value", "speed-label") +
        if (pairedControls) {
          listOf("seek-thumb", "seek-track", "position", "duration", "back-icon", "forward-icon")
        } else {
          emptyList()
        }
    parts.forEach { part ->
      val bounds =
        onNodeWithTag("playback-$part-expanded", useUnmergedTree = true)
          .assertIsDisplayed()
          .fetchSemanticsNode()
          .boundsInRoot
      val ink =
        (bounds.top.roundToInt() until bounds.bottom.roundToInt()).sumOf { y ->
          (bounds.left.roundToInt() until bounds.right.roundToInt()).count { x ->
            pixels[x, y].red < 0.6f
          }
        }
      assertThat(ink, "Expanded $part must draw after $step; found $ink foreground pixels")
        .isGreaterThanOrEqualTo(10)
    }
  }
}
