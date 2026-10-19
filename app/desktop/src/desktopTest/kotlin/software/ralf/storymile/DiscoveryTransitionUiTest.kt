package software.ralf.storymile

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isStrictlyBetween
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot

class DiscoveryTransitionUiTest {
  private val uiTestRule = DesktopUiTestRule()
  private val elements = listOf("artwork", "title", "author")

  @Test
  fun `featured artwork title and author move to the show and back`() {
    assertTransition(DpSize(412.dp, 916.dp), "featured")
  }

  @Test
  fun `search row shares its elements with the show and back`() {
    assertTransition(DpSize(412.dp, 916.dp), "search")
  }

  @Test
  fun `topic row transitions to the wide detail without matching the sidebar`() {
    assertTransition(DpSize(1280.dp, 800.dp), "topic")
  }

  @Test
  fun `scrolled featured card returns to its original position`() {
    assertTransition(DpSize(412.dp, 916.dp), "scrolled", podcastId = "small-wonders")
  }

  private fun assertTransition(
    size: DpSize,
    scenario: String,
    podcastId: String = "quiet-current",
  ) {
    uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
      composeRobot<DiscoveryRobot> {
        discover()
        when (scenario) {
          "search" -> search("Quiet")
          "topic" -> selectNature()
        }
        seePodcast(podcastId)
      }
      waitForIdle()
      val origin = elements.associateWith { bounds("discovery-$it-$podcastId") }
      mainClock.autoAdvance = false
      try {
        runOnUiThread { onNodeWithTag("discovery-podcast-$podcastId").performClick() }
        awaitEntry("show-screen")
        mainClock.advanceTimeBy(96)
        val forward = elements.associateWith { bounds("show-$it") }
        saveFrame("$scenario-forward", forward.getValue("artwork"))
        mainClock.advanceTimeBy(1_000)
        val detail = elements.associateWith { bounds("show-$it") }
        elements.forEach { element ->
          assertBetween(
            forward.getValue(element),
            origin.getValue(element),
            detail.getValue(element),
            "$scenario $element forward",
          )
        }

        runOnUiThread { onNodeWithTag("show-back").performClick() }
        awaitEntry("discovery-screen")
        mainClock.advanceTimeBy(96)
        elements.forEach { element ->
          assertBetween(
            bounds("discovery-$element-$podcastId"),
            detail.getValue(element),
            origin.getValue(element),
            "$scenario $element back",
          )
        }
        saveFrame("$scenario-back", bounds("discovery-artwork-$podcastId"))
        mainClock.advanceTimeBy(1_000)
        elements.forEach { element ->
          val restored = bounds("discovery-$element-$podcastId")
          assertThat((restored.center - origin.getValue(element).center).getDistance())
            .isLessThan(1f)
        }
      } finally {
        mainClock.autoAdvance = true
      }
    }
  }

  private fun ComposeUiTest.awaitEntry(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
      mainClock.advanceTimeByFrame()
      onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
    mainClock.advanceTimeByFrame()
  }

  private fun ComposeUiTest.bounds(tag: String): Rect = runOnUiThread {
    val matcher =
      if (tag.startsWith("discovery-")) {
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("discovery-screen"))
      } else {
        hasTestTag(tag)
      }
    val coordinates =
      onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun assertBetween(frame: Rect, start: Rect, end: Rect, label: String) {
    val distance = (end.center - start.center).getDistance()
    assertThat((frame.center - start.center).getDistance(), label)
      .isStrictlyBetween(1f, distance - 1f)
    assertThat((frame.center - end.center).getDistance(), label)
      .isStrictlyBetween(1f, distance - 1f)
    if (kotlin.math.abs(start.width - end.width) > 2f) {
      assertThat(frame.width, "$label width")
        .isStrictlyBetween(minOf(start.width, end.width), maxOf(start.width, end.width))
    }
  }

  private fun ComposeUiTest.saveFrame(name: String, artwork: Rect) {
    val image = onRoot().captureToImage()
    val pixels = image.toPixelMap()
    val ink =
      (artwork.top.toInt() until artwork.bottom.toInt()).sumOf { y ->
        (artwork.left.toInt() until artwork.right.toInt()).count { x ->
          pixels[x, y].luminance() < 0.3f
        }
      }
    assertThat(ink, "$name artwork remains visible").isGreaterThan(100)
    val file = File("build/reports/screenshots/discovery-transition-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(image.toAwtImage(), "png", file)
  }
}
