@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot

class PlayerLayoutUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `portrait player fits its controls by shrinking artwork`() {
    listOf(
        DpSize(360.dp, 640.dp) to 1f,
        DpSize(377.dp, 775.dp) to 1f,
        DesktopWindowSizes.phone to 1f,
        DpSize(599.dp, 900.dp) to 1f,
        DpSize(600.dp, 900.dp) to 1f,
        DpSize(900.dp, 1200.dp) to 1f,
        DpSize(377.dp, 850.dp) to 1.3f,
      )
      .forEach { (size, fontScale) ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = false, fontScale = fontScale) {
          composeRobot<AppShellRobot> { openPlayback() }
          assertPlayerFits()
          val file =
            File(
              "build/reports/screenshots/player-fit-${size.width.value.toInt()}x" +
                "${size.height.value.toInt()}-$fontScale.png",
            )
          file.parentFile.mkdirs()
          ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
        }
      }
  }

  @Test
  fun `artwork shrinks and grows when the player height changes`() {
    val size = mutableStateOf(DpSize(377.dp, 900.dp))
    uiTestRule.runRobotTest(windowSize = size.value, contentSize = size) {
      composeRobot<AppShellRobot> { openPlayback() }
      assertPlayerFits()
      val initialArtwork = artworkHeight()

      runOnIdle { size.value = DpSize(377.dp, 640.dp) }
      assertPlayerFits()
      assertThat(artworkHeight()).isLessThan(initialArtwork)

      runOnIdle { size.value = DpSize(377.dp, 900.dp) }
      assertPlayerFits()
      assertThat(artworkHeight()).isCloseTo(initialArtwork, 1f)
    }
  }

  private fun ComposeUiTest.assertPlayerFits() {
    onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
          hasAnyAncestor(hasTestTag("playback-screen")),
      )
      .fetchSemanticsNodes()
      .forEach { node ->
        assertThat(node.config[SemanticsProperties.VerticalScrollAxisRange].maxValue())
          .isEqualTo(0f)
      }
    listOf(
        "artwork-expanded",
        "title-expanded",
        "subtitle-expanded",
        "bookmark",
        "download",
        "seek",
        "play-pause",
        "speed",
        "sleep",
        "queue",
        "chapters",
        "current-chapter",
      )
      .forEach { part ->
        val node =
          onNodeWithTag("playback-$part", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertThat(node.boundsInRoot.height, "$part must not be clipped")
          .isCloseTo(node.size.height.toFloat(), 1f)
      }
    assertThat(artworkHeight()).isGreaterThan(0f)
  }

  private fun ComposeUiTest.artworkHeight(): Float {
    return onNodeWithTag("playback-artwork-expanded", useUnmergedTree = true)
      .fetchSemanticsNode()
      .boundsInRoot
      .height
  }
}
