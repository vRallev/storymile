package software.ralf.storymile

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isStrictlyBetween
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot

class PlayerTransitionUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `shared player controls follow a drag and reversal on phone tablet and desktop`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DpSize(1440.dp, 900.dp)).forEach {
      size ->
      uiTestRule.runRobotTest(windowSize = size) {
        val collapsed = controls(expanded = false)
        val collapsedSheetTop = sheetTop()
        composeRobot<AppShellRobot> { openPlayback() }
        val expanded = controls(expanded = true)
        composeRobot<AppShellRobot> { collapsePlayback() }
        assertControls(collapsed, expanded, progress = 0f)

        mainClock.autoAdvance = false
        try {
          runOnUiThread { onNodeWithTag("playback").performTouchInput { down(Offset(40f, 32f)) } }
          runOnUiThread {
            onRoot().performTouchInput { moveBy(Offset(0f, -collapsedSheetTop / 2)) }
          }
          mainClock.advanceTimeByFrame()
          val halfway = expansionProgress(collapsedSheetTop)
          assertThat(halfway).isBetween(0.25f, 0.75f)
          assertControls(collapsed, expanded, halfway)

          runOnUiThread { onRoot().performTouchInput { moveBy(Offset(0f, collapsedSheetTop / 4)) } }
          mainClock.advanceTimeByFrame()
          val reversed = expansionProgress(collapsedSheetTop)
          assertThat(reversed).isStrictlyBetween(0f, halfway)
          assertControls(collapsed, expanded, reversed)

          runOnUiThread { onRoot().performTouchInput { moveBy(Offset(0f, collapsedSheetTop)) } }
        } finally {
          runOnUiThread {
            onRoot().performTouchInput {
              advanceEventTime(100)
              up()
            }
          }
          mainClock.autoAdvance = true
        }
        assertControls(collapsed, expanded, progress = 0f)
      }
    }
  }

  @Test
  fun `shared player controls animate from their current bounds when opening and closing`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DpSize(1440.dp, 900.dp)).forEach {
      size ->
      uiTestRule.runRobotTest(windowSize = size) {
        val collapsed = controls(expanded = false)
        val collapsedSheetTop = sheetTop()
        composeRobot<AppShellRobot> { openPlayback() }
        val expanded = controls(expanded = true)
        composeRobot<AppShellRobot> { collapsePlayback() }
        assertControls(collapsed, expanded, progress = 0f)

        mainClock.autoAdvance = false
        try {
          composeRobot<AppShellRobot> { openPlayback() }
          val openingStart = advanceToProgress(collapsedSheetTop) { it > 0f }
          assertThat(openingStart).isLessThan(1f)
          assertControls(collapsed, expanded, openingStart)
          val opening = advanceToProgress(collapsedSheetTop) { it in 0.2f..0.8f }
          assertControls(collapsed, expanded, opening)

          mainClock.autoAdvance = true
          assertControls(collapsed, expanded, progress = 1f)
          mainClock.autoAdvance = false
          composeRobot<AppShellRobot> { collapsePlayback() }
          val closingStart = advanceToProgress(collapsedSheetTop) { it < 1f }
          assertThat(closingStart).isGreaterThan(0f)
          assertControls(collapsed, expanded, closingStart)
          val closing = advanceToProgress(collapsedSheetTop) { it in 0.2f..0.8f }
          assertControls(collapsed, expanded, closing)
        } finally {
          mainClock.autoAdvance = true
        }
        assertControls(collapsed, expanded, progress = 0f)
      }
    }
  }

  @Test
  fun `collapsing a scrolled player starts at the visible control positions`() {
    listOf(DpSize(360.dp, 480.dp), DpSize(900.dp, 480.dp)).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size) {
        val collapsed = controls(expanded = false)
        val collapsedSheetTop = sheetTop()
        composeRobot<AppShellRobot> { openPlayback() }
        val original = controls(expanded = true)
        runOnUiThread { onNodeWithTag("playback-current-chapter").performScrollTo() }
        val scrolled = controls(expanded = true)
        assertThat(scrolled.title.bounds.top).isLessThan(original.title.bounds.top - 20f)
        assertThat(scrolled.button.top).isLessThan(original.button.top - 20f)

        mainClock.autoAdvance = false
        try {
          composeRobot<AppShellRobot> { collapsePlayback() }
          val closingStart = advanceToProgress(collapsedSheetTop) { it < 1f }
          assertThat(closingStart).isGreaterThan(0f)
          assertControls(collapsed, scrolled, closingStart)
          val closing = advanceToProgress(collapsedSheetTop) { it in 0.2f..0.8f }
          assertControls(collapsed, scrolled, closing)
        } finally {
          mainClock.autoAdvance = true
        }
        assertControls(collapsed, scrolled, progress = 0f)
        composeRobot<AppShellRobot> { openPlayback() }
        assertControls(collapsed, scrolled, progress = 1f)
      }
    }
  }

  private fun ComposeUiTest.controls(expanded: Boolean): Controls {
    val suffix = if (expanded) "expanded" else "collapsed"
    return Controls(
      title = label("playback-title-$suffix"),
      subtitle = label("playback-subtitle-$suffix"),
      button = bounds(if (expanded) "playback-play-pause" else "playback-bar-play-pause"),
    )
  }

  private fun ComposeUiTest.label(tag: String): Label {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(
      SemanticsActions.GetTextLayoutResult,
    ) {
      it(layouts)
    }
    return Label(bounds(tag), layouts.single().layoutInput.style.fontSize.value)
  }

  private fun ComposeUiTest.bounds(tag: String): Rect {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    // Include graphics-layer scale while ignoring the control's original scroll viewport.
    return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun ComposeUiTest.sheetTop(): Float =
    onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top

  private fun ComposeUiTest.expansionProgress(collapsedSheetTop: Float): Float =
    1f - sheetTop() / collapsedSheetTop

  private fun ComposeUiTest.advanceToProgress(
    collapsedSheetTop: Float,
    predicate: (Float) -> Boolean,
  ): Float {
    repeat(60) {
      mainClock.advanceTimeByFrame()
      val progress = expansionProgress(collapsedSheetTop)
      if (predicate(progress)) {
        return progress
      }
    }
    error("Player did not reach the expected animation progress")
  }

  private fun ComposeUiTest.assertControls(
    collapsed: Controls,
    expanded: Controls,
    progress: Float,
  ) {
    val actual = controls(expanded = progress > 0f)
    val window = onRoot().fetchSemanticsNode().boundsInRoot
    val stacked = window.width < 600f || window.width <= window.height
    val arc =
      if (stacked) {
        Offset(window.width * 0.1f, if (window.width < 600f) 64f else 96f)
      } else {
        Offset.Zero
      }
    assertLabel(collapsed.title, expanded.title, actual.title, progress, "title", arc)
    assertLabel(collapsed.subtitle, expanded.subtitle, actual.subtitle, progress, "subtitle", arc)
    val expectedButton =
      Rect(
        left = interpolate(collapsed.button.left, expanded.button.left, progress),
        top = interpolate(collapsed.button.top, expanded.button.top, progress),
        right = interpolate(collapsed.button.right, expanded.button.right, progress),
        bottom = interpolate(collapsed.button.bottom, expanded.button.bottom, progress),
      )
    assertNear(actual.button.left, expectedButton.left, "button left at $progress")
    assertNear(actual.button.top, expectedButton.top, "button top at $progress")
    assertNear(actual.button.right, expectedButton.right, "button right at $progress")
    assertNear(actual.button.bottom, expectedButton.bottom, "button bottom at $progress")
    assertNear(actual.button.width, actual.button.height, "button remains round at $progress")
  }

  private fun assertLabel(
    collapsed: Label,
    expanded: Label,
    actual: Label,
    progress: Float,
    name: String,
    arc: Offset,
  ) {
    val layout = if (progress > 0f) expanded else collapsed
    val scale = interpolate(collapsed.fontSize, expanded.fontSize, progress) / layout.fontSize
    val arcFraction = EaseOutCubic.transform(progress)
    assertNear(
      actual.bounds.left,
      interpolate(collapsed.bounds.left, expanded.bounds.left, progress) +
        arc.x * 4f * arcFraction * (1f - arcFraction),
      "$name left at $progress",
    )
    assertNear(
      actual.bounds.top,
      interpolate(collapsed.bounds.top, expanded.bounds.top, progress) +
        arc.y * 4f * arcFraction * (1f - arcFraction),
      "$name top at $progress",
    )
    assertNear(actual.bounds.width, layout.bounds.width * scale, "$name width at $progress")
    assertNear(actual.bounds.height, layout.bounds.height * scale, "$name height at $progress")
  }

  private fun interpolate(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

  private fun assertNear(actual: Float, expected: Float, description: String) {
    assertThat(actual, description).isCloseTo(expected, 1f)
  }

  private data class Controls(val title: Label, val subtitle: Label, val button: Rect)

  private data class Label(val bounds: Rect, val fontSize: Float)
}
