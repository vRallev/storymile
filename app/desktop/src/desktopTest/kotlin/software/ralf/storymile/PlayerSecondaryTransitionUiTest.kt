package software.ralf.storymile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot

class PlayerSecondaryTransitionUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `seek time skip and speed controls move from the first click frame in both directions`() {
    listOf(DpSize(900.dp, 760.dp), DpSize(1440.dp, 900.dp)).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
        val parts = sharedParts(size)
        val collapsed = snapshot(parts, expanded = false)
        val collapsedTop = sheetTop()
        mainClock.autoAdvance = false
        try {
          composeRobot<AppShellRobot> { openPlayback() }
          val firstProgress = advanceToProgress(collapsedTop) { it > 0f }
          assertThat(firstProgress).isLessThan(1f)
          val first = snapshot(parts, expanded = true)
          val middleProgress = advanceToProgress(collapsedTop) { it in 0.3f..0.7f }
          val middle = snapshot(parts, expanded = true)
          mainClock.autoAdvance = true
          val expanded = snapshot(parts, expanded = true)
          assertShared(collapsed, expanded, first, firstProgress)
          assertShared(collapsed, expanded, middle, middleProgress)

          mainClock.autoAdvance = false
          composeRobot<AppShellRobot> { collapsePlayback() }
          val closingStart = advanceToProgress(collapsedTop) { it < 1f }
          assertThat(closingStart).isGreaterThan(0f)
          assertShared(collapsed, expanded, snapshot(parts, expanded = true), closingStart)
          val closing = advanceToProgress(collapsedTop) { it in 0.3f..0.7f }
          assertShared(collapsed, expanded, snapshot(parts, expanded = true), closing)
        } finally {
          mainClock.autoAdvance = true
        }
        snapshot(parts, expanded = false)
      }
    }
  }

  @Test
  fun `shared secondary controls follow drag reversal without changing slider thickness`() {
    listOf(DpSize(900.dp, 760.dp), DpSize(1440.dp, 900.dp)).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
        val parts = sharedParts(size)
        val collapsed = snapshot(parts, expanded = false)
        val collapsedTop = sheetTop()
        composeRobot<AppShellRobot> { openPlayback() }
        val expanded = snapshot(parts, expanded = true)
        composeRobot<AppShellRobot> { collapsePlayback() }
        waitForIdle()
        mainClock.autoAdvance = false
        try {
          runOnUiThread { onNodeWithTag("playback").performTouchInput { down(Offset(40f, 32f)) } }
          listOf(0.1f, 0.2f, 0.35f, 0.5f, 0.65f, 0.4f).forEach { target ->
            dragToProgress(collapsedTop, target)
            val progress = 1f - sheetTop() / collapsedTop
            assertShared(collapsed, expanded, snapshot(parts, expanded = true), progress)
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
  fun `controls absent from the collapsed phone and tablet fade into the expanded player`() {
    listOf(DpSize(480.dp, 1200.dp), DpSize(900.dp, 760.dp)).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
        val phone = size.width < 600.dp
        val parts = buildList {
          add("back-icon")
          add("forward-icon")
          add("speed-label")
          if (phone) {
            addAll(listOf("seek-thumb", "seek-track", "position", "duration", "speed-value"))
          }
        }
        parts.forEach {
          onNodeWithTag("playback-$it-collapsed", useUnmergedTree = true).assertDoesNotExist()
        }
        val collapsedTop = sheetTop()
        composeRobot<AppShellRobot> { openPlayback() }
        val expanded = snapshot(parts, expanded = true)
        composeRobot<AppShellRobot> { collapsePlayback() }
        waitForIdle()
        mainClock.autoAdvance = false
        try {
          runOnUiThread { onNodeWithTag("playback").performTouchInput { down(Offset(40f, 32f)) } }
          val targets = if (phone) listOf(0.85f, 0.95f, 0.8f) else listOf(0.9f, 0.97f, 0.9f)
          targets.forEach { target ->
            dragToProgress(collapsedTop, target)
            val progress = 1f - sheetTop() / collapsedTop
            val actual = snapshot(parts, expanded = true)
            parts.forEach { part ->
              val opacity = actual.getValue(part)
              val full = expanded.getValue(part)
              val expected = 1f - (1f - full.minimumRed) * progress
              assertNear(
                opacity.minimumRed,
                expected,
                "$part fades at $progress ${opacity.bounds}",
                tolerance = 0.07f,
              )
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

  private fun sharedParts(size: DpSize): List<String> = buildList {
    addAll(
      listOf(
        "seek-thumb",
        "seek-track",
        "position",
        "duration",
        "speed-value",
        "subtitle",
        "play-pause-button",
      ),
    )
    if (size.width >= 1000.dp) {
      addAll(
        listOf(
          "back-icon",
          "forward-icon",
          "back-button",
          "forward-button",
          "speed-label",
        ),
      )
    }
  }

  private fun ComposeUiTest.snapshot(parts: List<String>, expanded: Boolean): Map<String, Part> {
    val suffix = if (expanded) "expanded" else "collapsed"
    val rectangles = parts.associateWith { part ->
      val tag =
        if (part.endsWith("-button")) {
          "playback-${if (expanded) "" else "bar-"}${part.removeSuffix("-button")}"
        } else {
          "playback-$part-$suffix"
        }
      val coordinates =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
      coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
    }
    val pixels = onRoot().captureToImage().toPixelMap()
    return rectangles.mapValues { (key, bounds) ->
      // The moving play button can cross the track. Sample its active leading section.
      val inkBounds =
        if (key == "seek-track") bounds.copy(right = bounds.left + bounds.width * 0.2f) else bounds
      Part(bounds, pixels.minimumRed(inkBounds), pixels.inkBounds(bounds))
    }
  }

  private fun PixelMap.minimumRed(bounds: Rect): Float {
    val left = floor(bounds.left).toInt().coerceAtLeast(0)
    val right = ceil(bounds.right).toInt().coerceAtMost(width)
    val top = floor(bounds.top).toInt().coerceAtLeast(0)
    val bottom = ceil(bounds.bottom).toInt().coerceAtMost(height)
    assertThat(right, "Control is outside the screenshot: $bounds").isGreaterThan(left)
    assertThat(bottom, "Control is outside the screenshot: $bounds").isGreaterThan(top)
    return (top until bottom).minOf { y -> (left until right).minOf { x -> this[x, y].red } }
  }

  private fun PixelMap.inkBounds(bounds: Rect): Rect {
    var ink = Rect.Zero
    for (y in
      floor(bounds.top).toInt().coerceAtLeast(0) until
        ceil(bounds.bottom).toInt().coerceAtMost(height)) {
      for (x in
        floor(bounds.left).toInt().coerceAtLeast(0) until
          ceil(bounds.right).toInt().coerceAtMost(width)) {
        if (this[x, y].red < 0.6f) {
          val pixel = Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f)
          ink =
            if (ink.isEmpty) pixel
            else
              Rect(
                minOf(ink.left, pixel.left),
                minOf(ink.top, pixel.top),
                maxOf(ink.right, pixel.right),
                maxOf(ink.bottom, pixel.bottom),
              )
        }
      }
    }
    return ink
  }

  private fun assertShared(
    collapsed: Map<String, Part>,
    expanded: Map<String, Part>,
    actual: Map<String, Part>,
    progress: Float,
  ) {
    val wide = actual.containsKey("back-icon")
    actual.forEach { (key, part) ->
      val start = collapsed.getValue(key)
      val end = expanded.getValue(key)
      val arc = arcFor(key, wide)
      assertNear(
        part.bounds.left,
        interpolate(start.bounds.left, end.bounds.left, progress) +
          arc.x * 4f * progress * (1f - progress),
        "$key left at $progress",
      )
      assertNear(
        part.bounds.top,
        interpolate(start.bounds.top, end.bounds.top, progress) +
          arc.y * 4f * progress * (1f - progress),
        "$key top at $progress",
      )
      assertThat(part.minimumRed, "$key must stay opaque at $progress")
        .isLessThanOrEqualTo(maxOf(start.minimumRed, end.minimumRed) + 0.08f)
      if (
        key.endsWith("icon") || key.endsWith("button") || key == "seek-thumb" || key == "seek-track"
      ) {
        assertNear(
          part.bounds.width,
          interpolate(start.bounds.width, end.bounds.width, progress),
          "$key width at $progress",
        )
        assertNear(
          part.bounds.height,
          interpolate(start.bounds.height, end.bounds.height, progress),
          "$key height at $progress",
        )
      }
      when (key) {
        "seek-thumb" -> {
          assertNear(part.bounds.width, 16f, "Thumb width")
          assertNear(part.bounds.height, 16f, "Thumb height")
        }
        "seek-track" -> assertNear(part.bounds.height, 4f, "Track thickness")
        "back-icon",
        "forward-icon" ->
          assertNear(part.bounds.width, 28f + 20f * progress, "$key interpolates its icon size")
        "back-button",
        "forward-button" ->
          assertNear(part.bounds.width, 48f + 16f * progress, "$key interpolates its touch target")
      }
    }
    assertLabelsAvoidTransport(actual, expanded, progress)
    val position = actual.getValue("position").projectInk(expanded.getValue("position"))
    val subtitle = actual.getValue("subtitle").projectInk(expanded.getValue("subtitle"))
    assertThat(
        position.overlaps(subtitle),
        "Elapsed time ink $position overlaps subtitle ink $subtitle at $progress",
      )
      .isFalse()
  }

  private fun arcFor(key: String, wide: Boolean): Offset =
    when (key) {
      "duration" -> if (wide) Offset(0f, -56f) else Offset(-72f, -28f)
      "speed-value",
      "speed-label" -> Offset(0f, 80f)
      else -> Offset.Zero
    }

  private fun assertLabelsAvoidTransport(
    parts: Map<String, Part>,
    expanded: Map<String, Part>,
    progress: Float,
  ) {
    val play = parts.getValue("play-pause-button").bounds
    val icons = listOf("back-icon", "forward-icon").filter { parts.containsKey(it) }
    val labels =
      if (icons.isEmpty()) listOf("duration")
      else listOf("position", "duration", "speed-value", "speed-label")
    labels.forEach { key ->
      val label = parts.getValue(key).projectInk(expanded.getValue(key))
      icons.forEach { icon ->
        val iconInk = parts.getValue(icon).projectInk(expanded.getValue(icon))
        assertThat(
            label.overlaps(iconInk),
            "$key ink $label overlaps $icon ink $iconInk at $progress",
          )
          .isFalse()
      }
      val nearestX = play.center.x.coerceIn(label.left, label.right)
      val nearestY = play.center.y.coerceIn(label.top, label.bottom)
      val distanceSquared =
        (nearestX - play.center.x) * (nearestX - play.center.x) +
          (nearestY - play.center.y) * (nearestY - play.center.y)
      assertThat(distanceSquared, "$key ink $label overlaps play/pause $play at $progress")
        .isGreaterThanOrEqualTo(play.width * play.width / 4f)
    }
  }

  private fun Part.projectInk(reference: Part): Rect {
    assertThat(reference.ink.isEmpty, "Expected visible ink in the native endpoint").isFalse()
    val scaleX = bounds.width / reference.bounds.width
    val scaleY = bounds.height / reference.bounds.height
    return Rect(
      bounds.left + (reference.ink.left - reference.bounds.left) * scaleX,
      bounds.top + (reference.ink.top - reference.bounds.top) * scaleY,
      bounds.left + (reference.ink.right - reference.bounds.left) * scaleX,
      bounds.top + (reference.ink.bottom - reference.bounds.top) * scaleY,
    )
  }

  private fun ComposeUiTest.sheetTop(): Float =
    onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top

  private fun ComposeUiTest.dragToProgress(collapsedTop: Float, target: Float) {
    val distance = collapsedTop * (1f - target) - sheetTop()
    runOnUiThread { onRoot().performTouchInput { moveBy(Offset(0f, distance)) } }
    mainClock.advanceTimeByFrame()
  }

  private fun ComposeUiTest.advanceToProgress(
    collapsedTop: Float,
    predicate: (Float) -> Boolean,
  ): Float {
    repeat(60) {
      mainClock.advanceTimeByFrame()
      val progress = 1f - sheetTop() / collapsedTop
      if (predicate(progress)) {
        return progress
      }
    }
    error("Player did not reach expected animation progress")
  }

  private fun interpolate(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

  private fun assertNear(
    actual: Float,
    expected: Float,
    description: String,
    tolerance: Float = 1f,
  ) {
    assertThat(actual, description).isCloseTo(expected, tolerance)
  }

  private data class Part(val bounds: Rect, val minimumRed: Float, val ink: Rect)
}
