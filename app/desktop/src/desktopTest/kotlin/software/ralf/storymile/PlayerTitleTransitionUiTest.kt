@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isStrictlyBetween
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot

class PlayerTitleTransitionUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `whole player header stays hidden through halfway during expansion and reversal`() {
    uiTestRule.runPhoneRobotTest {
      val collapsedTop = sheetTop()
      mainClock.autoAdvance = false
      try {
        runOnUiThread { onNodeWithTag("playback").performTouchInput { down(Offset(40f, 32f)) } }
        listOf(0.3f, 0.49f, 0.51f, 0.75f, 0.95f, 0.75f, 0.51f, 0.49f, 0.3f, 0.75f).forEach { target
          ->
          val distance = collapsedTop * (1f - target) - sheetTop()
          runOnUiThread { onRoot().performTouchInput { moveBy(Offset(0f, distance)) } }
          mainClock.advanceTimeByFrame()
          val progress = 1f - sheetTop() / collapsedTop
          listOf(
              onNodeWithText("Now playing"),
              onNodeWithTag("playback-more"),
              onNodeWithTag("playback-collapse"),
            )
            .forEach { node ->
              if (progress <= 0.5f) {
                node.assertIsNotDisplayed()
              } else {
                node.assertIsDisplayed()
              }
            }
        }
      } finally {
        runOnUiThread {
          onRoot().performTouchInput {
            advanceEventTime(100)
            up()
          }
        }
        mainClock.autoAdvance = true
      }
    }
  }

  @Test
  fun `title and subtitle avoid artwork throughout expansion and reversal`() {
    listOf(
        DpSize(390.dp, 844.dp),
        DpSize(480.dp, 1200.dp),
        DpSize(599.dp, 960.dp),
        DpSize(600.dp, 960.dp),
        DpSize(840.dp, 1200.dp),
        DpSize(900.dp, 760.dp),
        DpSize(900.dp, 480.dp),
        DpSize(1440.dp, 900.dp),
        DpSize(360.dp, 480.dp),
      )
      .forEach { size ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
          val collapsedTop = sheetTop()
          composeRobot<AppShellRobot> { openPlayback() }
          composeRobot<AppShellRobot> { collapsePlayback() }
          waitForIdle()
          mainClock.autoAdvance = false
          try {
            runOnUiThread { onNodeWithTag("playback").performTouchInput { down(Offset(40f, 32f)) } }
            val steps = (1..49).map { it / 50f }
            (steps + steps.reversed()).forEach { target ->
              val distance = collapsedTop * (1f - target) - sheetTop()
              runOnUiThread { onRoot().performTouchInput { moveBy(Offset(0f, distance)) } }
              mainClock.advanceTimeByFrame()
              val artwork = bounds("playback-artwork-expanded")
              if (size.width == 390.dp && target in listOf(0.1f, 0.2f, 0.5f)) {
                val file = File("build/reports/screenshots/player-title-path-$target.png")
                file.parentFile.mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
              }
              listOf("title", "subtitle").forEach { key ->
                val label = bounds("playback-$key-expanded")
                assertThat(
                    label.overlaps(artwork),
                    "$key $label overlaps artwork $artwork at $target in $size",
                  )
                  .isFalse()
              }
            }
          } finally {
            runOnUiThread {
              onRoot().performTouchInput {
                advanceEventTime(100)
                up()
              }
            }
            mainClock.autoAdvance = true
          }
        }
      }
  }

  @Test
  fun `phone title retains its final glyph throughout expansion and reversal`() {
    val frames = mutableListOf<TitleFrame>()
    uiTestRule.runPhoneRobotTest(darkTheme = false) {
      val collapsed = titleLayout("playback-title-collapsed")
      val collapsedTop = sheetTop()
      composeRobot<AppShellRobot> { openPlayback() }
      val expanded = titleLayout("playback-title-expanded")
      assertThat(collapsed.lineCount).isEqualTo(1)
      assertThat(expanded.lineCount).isEqualTo(1)
      assertThat(collapsed.isLineEllipsized(0)).isFalse()
      assertThat(expanded.isLineEllipsized(0)).isFalse()
      composeRobot<AppShellRobot> { collapsePlayback() }
      waitForIdle()

      mainClock.autoAdvance = false
      try {
        runOnUiThread { onNodeWithTag("playback").performTouchInput { down(Offset(40f, 32f)) } }
        listOf(0.05f, 0.1f, 0.2f, 0.35f, 0.5f, 0.7f, 0.5f, 0.2f, 0.05f).forEach { target ->
          val distance = collapsedTop * (1f - target) - sheetTop()
          runOnUiThread { onRoot().performTouchInput { moveBy(Offset(0f, distance)) } }
          mainClock.advanceTimeByFrame()
          val progress = 1f - sheetTop() / collapsedTop
          assertThat(progress).isStrictlyBetween(0f, 1f)
          val node =
            onNodeWithTag("playback-title-expanded", useUnmergedTree = true).fetchSemanticsNode()
          val coordinates = node.layoutInfo.coordinates
          val origin =
            coordinates
              .findRootCoordinates()
              .localBoundingBoxOf(coordinates, clipBounds = false)
              .topLeft
          frames +=
            TitleFrame(
              pixels = onRoot().captureToImage().toPixelMap(),
              origin = origin,
              progress = progress,
              text = expanded.layoutInput.text.text,
              style = lerp(collapsed.layoutInput.style, expanded.layoutInput.style, progress),
              density = node.layoutInfo.density,
            )
        }
      } finally {
        runOnUiThread {
          onRoot().performTouchInput {
            advanceEventTime(100)
            up()
          }
        }
        mainClock.autoAdvance = true
      }
    }

    frames.forEach { frame ->
      runDesktopComposeUiTest(width = 480, height = 100) {
        setContent {
          CompositionLocalProvider(LocalDensity provides frame.density) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
              BasicText(
                frame.text,
                style = frame.style.copy(color = Color.Black),
                modifier = Modifier.testTag("reference"),
              )
            }
          }
        }
        val layout = titleLayout("reference")
        val reference = onRoot().captureToImage().toPixelMap()
        assertFinalGlyph(frame, reference, layout)
      }
    }
  }

  private fun ComposeUiTest.titleLayout(tag: String): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(
      SemanticsActions.GetTextLayoutResult,
    ) {
      it(layouts)
    }
    return layouts.single()
  }

  private fun ComposeUiTest.sheetTop(): Float =
    onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top

  private fun ComposeUiTest.bounds(tag: String): Rect {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun assertFinalGlyph(frame: TitleFrame, reference: PixelMap, layout: TextLayoutResult) {
    val last = layout.getBoundingBox(frame.text.lastIndex)
    val ink = mutableListOf<Offset>()
    for (y in
      floor(last.top).toInt().coerceAtLeast(0) until
        ceil(last.bottom).toInt().coerceAtMost(reference.height)) {
      for (x in
        floor(last.left).toInt().coerceAtLeast(0) until
          ceil(last.right).toInt().coerceAtMost(reference.width)) {
        if (reference[x, y].luminance() < 0.4f) {
          ink += Offset(x.toFloat(), y.toFloat())
        }
      }
    }
    assertThat(ink).isNotEmpty()
    // Ellipsis dots can occupy the same width. Require the letter's ink above the baseline.
    val upperEdge = ink.minOf { it.y }
    val upperInk = ink.filter {
      it.y <= upperEdge + (ink.maxOf { point -> point.y } - upperEdge) * 0.65f
    }
    val matched = upperInk.count { point ->
      val x = (frame.origin.x + point.x).roundToInt()
      val y = (frame.origin.y + point.y).roundToInt()
      (-1..1).any { dy ->
        (-1..1).any { dx -> frame.pixels[x + dx, y + dy].luminance() < 0.4f }
      }
    }
    assertThat(
        matched.toFloat(),
        "The final title glyph is missing at progress ${frame.progress}: $matched/${upperInk.size} reference pixels remain",
      )
      .isGreaterThanOrEqualTo(upperInk.size * 0.85f)
  }

  private data class TitleFrame(
    val pixels: PixelMap,
    val origin: Offset,
    val progress: Float,
    val text: String,
    val style: TextStyle,
    val density: Density,
  )
}
