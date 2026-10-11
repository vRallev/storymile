@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThan
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test

class PlayerSliderTransitionUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `slider thumb covers the track throughout expansion and reversal`() {
    listOf(DpSize(1440.dp, 900.dp), DpSize(900.dp, 760.dp)).forEach { size ->
      listOf(true, false).forEach { darkTheme ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
          val collapsed = thumbBounds(expanded = false)
          val initial = onRoot().captureToImage().toPixelMap()
          val color = initial[collapsed.center.x.roundToInt(), collapsed.center.y.roundToInt()]
          val collapsedTop = sheetTop()
          mainClock.autoAdvance = false
          try {
            onNodeWithTag("playback").performTouchInput { down(Offset(40f, 32f)) }
            listOf(0.2f, 0.5f, 0.75f, 0.4f, 0.15f).forEach { target ->
              val distance = collapsedTop * (1f - target) - sheetTop()
              onRoot().performTouchInput { moveBy(Offset(0f, distance)) }
              mainClock.advanceTimeByFrame()
              val bounds = thumbBounds(expanded = true)
              val screenshot = onRoot().captureToImage()
              if (darkTheme && size.width == 1440.dp && target == 0.2f) {
                val file = File("build/reports/screenshots/player-slider-dark-transition.png")
                file.parentFile.mkdirs()
                ImageIO.write(screenshot.toAwtImage(), "png", file)
              }
              assertThumbAboveTrack(screenshot.toPixelMap(), bounds, color)
            }
          } finally {
            onRoot().performTouchInput {
              advanceEventTime(100)
              up()
            }
            mainClock.autoAdvance = true
          }
        }
      }
    }
  }

  private fun ComposeUiTest.thumbBounds(expanded: Boolean): Rect {
    val suffix = if (expanded) "expanded" else "collapsed"
    val coordinates =
      onNodeWithTag("playback-seek-thumb-$suffix", useUnmergedTree = true)
        .fetchSemanticsNode()
        .layoutInfo
        .coordinates
    return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun ComposeUiTest.sheetTop(): Float =
    onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top

  private fun assertThumbAboveTrack(pixels: PixelMap, bounds: Rect, color: Color) {
    val x = bounds.center.x.roundToInt()
    val y = bounds.center.y.roundToInt()
    assertColor(pixels[x, y + 4], color, "thumb interior below the track")
    // The inactive track crosses the right half of the thumb if its overlay draws last.
    listOf(2, 4).forEach { dx ->
      for (dy in -1..1) {
        assertColor(pixels[x + dx, y + dy], color, "thumb interior over the track at ($dx, $dy)")
      }
    }
  }

  private fun assertColor(actual: Color, expected: Color, description: String) {
    val difference =
      maxOf(
        abs(actual.red - expected.red),
        abs(actual.green - expected.green),
        abs(actual.blue - expected.blue),
      )
    assertThat(difference, "$description: expected $expected, got $actual").isLessThan(0.025f)
  }
}
