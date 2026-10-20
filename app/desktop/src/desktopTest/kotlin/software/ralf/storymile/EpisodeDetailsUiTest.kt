package software.ralf.storymile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.discovery.ShowRobot

class EpisodeDetailsUiTest {
  private val uiTestRule = DesktopUiTestRule()
  private val elements = listOf("artwork", "title", "subtitle", "description", "duration")

  @Test
  fun `discovery episode shares artwork and text with details and returns to its row`() {
    assertNavigation(fromShow = false, size = DpSize(width = 412.dp, height = 916.dp))
  }

  @Test
  fun `compact show episode opens reusable details and returns to the selected show`() {
    assertNavigation(fromShow = true, size = DpSize(width = 599.dp, height = 800.dp))
  }

  @Test
  fun `phone show text scales without clipping`() {
    assertNavigation(
      fromShow = true,
      size = DpSize(width = 403.dp, height = 700.dp),
      darkTheme = true,
    )
  }

  @Test
  fun `episode transition preserves multiline titles with large fonts`() {
    assertNavigation(
      fromShow = true,
      size = DpSize(width = 480.dp, height = 1000.dp),
      fontScale = 2f,
    )
  }

  @Test
  fun `episode back preview survives cancellation and reopening the same episode`() {
    uiTestRule.runRobotTest(windowSize = DesktopWindowSizes.phone) {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      composeRobot<ShowRobot> { pressPreviewActions() }
      runOnUiThread {
        onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-0"))
      }
      waitForIdle()
      val origin = listOf("artwork", "title", "subtitle").associateWith { bounds("episode-$it-0") }
      val root = onRoot().fetchSemanticsNode().boundsInRoot
      val distance = minOf(300f, root.width / 2)
      repeat(2) {
        runOnUiThread { onNodeWithTag("episode-0").performClick() }
        waitForIdle()
        onNodeWithTag("episode-details-screen").assertIsDisplayed()
        val destination = origin.keys.associateWith { bounds("episode-details-$it") }

        mouse {
          moveTo(Offset(x = 2f, y = root.height / 2))
          press()
          moveTo(Offset(x = 2f + distance * 0.7f, y = root.height / 2))
        }
        onNodeWithTag("show-screen").assertExists()
        val preview = bounds("episode-details-artwork")
        assertThat((preview.center - destination.getValue("artwork").center).getDistance())
          .isGreaterThan(1f)
        // The shared bounds resize; the parent navigation transition only slides and fades.
        assertThat(preview.width).isGreaterThan(origin.getValue("artwork").width)
        assertThat(preview.width).isLessThan(destination.getValue("artwork").width)

        mouse { moveTo(Offset(x = 2f + distance * 0.2f, y = root.height / 2)) }
        val reversed = bounds("episode-details-artwork")
        assertThat((reversed.center - destination.getValue("artwork").center).getDistance())
          .isLessThan((preview.center - destination.getValue("artwork").center).getDistance())
        assertThat(reversed.width).isGreaterThan(preview.width)
        mouse { release() }
        onNodeWithTag("episode-details-screen").assertIsDisplayed()
        onNodeWithTag("show-screen").assertDoesNotExist()
        destination.forEach { (element, expected) ->
          assertBoundsNear(actual = bounds("episode-details-$element"), expected = expected)
        }

        mouse {
          moveTo(Offset(x = 2f, y = root.height / 2))
          press()
          moveTo(Offset(x = 2f + distance * 0.7f, y = root.height / 2))
        }
        mouse { release() }
        waitUntil(timeoutMillis = 5_000) {
          onAllNodes(hasTestTag("episode-details-screen")).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("episode-0").assertIsDisplayed()
        origin.forEach { (element, expected) ->
          assertBoundsNear(actual = bounds("episode-$element-0"), expected = expected)
        }
      }
    }
  }

  private fun assertNavigation(
    fromShow: Boolean,
    size: DpSize,
    darkTheme: Boolean = false,
    fontScale: Float = 1f,
  ) {
    uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme, fontScale = fontScale) {
      composeRobot<DiscoveryRobot> {
        discover()
        seePodcast("quiet-current")
        if (fromShow) {
          openPodcast("quiet-current")
        }
      }
      if (fromShow) {
        composeRobot<ShowRobot> { pressPreviewActions() }
      }
      val listTag = if (fromShow) "show-detail" else "discovery-list"
      runOnUiThread {
        onNodeWithTag(listTag).performScrollToNode(hasTestTag("episode-0"))
      }
      waitForIdle()
      val tabsPixels = onRoot().captureToImage().toPixelMap()
      val origin = elements.associateWith { bounds("episode-$it-0") }
      val scenario =
        "${if (fromShow) "show" else "discovery"}-${size.width.value.toInt()}-$fontScale"
      mainClock.autoAdvance = false
      try {
        runOnUiThread { onNodeWithTag("episode-0").performClick() }
        awaitEntry("episode-details-screen")
        val forward =
          sampleTransition(
            prefix = "episode-details-",
            suffix = "",
            scenario = "$scenario-forward",
            tabsPixels = tabsPixels,
          )
        mainClock.advanceTimeBy(1_000)
        val destination = elements.associateWith { bounds("episode-details-$it") }
        val title = textLayout("episode-details-title")
        forward.forEach { frame ->
          assertThat(frame.title.size).isEqualTo(title.size)
          assertThat(frame.title.lineCount).isEqualTo(title.lineCount)
          assertThat(frame.title.didOverflowHeight).isFalse()
          assertThat(frame.title.getLineEnd(frame.title.lineCount - 1))
            .isEqualTo(title.layoutInput.text.length)
          for (line in 0 until frame.title.lineCount) {
            assertThat(frame.title.isLineEllipsized(line)).isFalse()
          }
        }
        assertThat(forward.first().bounds.getValue("title").height)
          .isLessThan(destination.getValue("title").height)
        elements.forEach { element ->
          assertThat(forward[10].bounds.getValue(element), "$element moves")
            .isNotEqualTo(origin.getValue(element))
          assertThat(forward[10].bounds.getValue(element), "$element keeps moving")
            .isNotEqualTo(destination.getValue(element))
        }
        onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 1")
        onNodeWithTag("episode-details-duration").assertTextEquals("30:00")
        saveFrame("$scenario-details")
        runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
        awaitEntry(if (fromShow) "show-screen" else "discovery-screen")
        sampleTransition(
          prefix = "episode-",
          suffix = "-0",
          scenario = "$scenario-back",
          tabsPixels = tabsPixels,
        )
        mainClock.advanceTimeBy(1_000)
        elements.forEach { element ->
          assertThat(
              (bounds("episode-$element-0").center - origin.getValue(element).center).getDistance(),
            )
            .isLessThan(1f)
        }
        onNodeWithTag("episode-0").assertIsDisplayed()
        onNodeWithTag("episode-details-screen").assertDoesNotExist()
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

  private fun ComposeUiTest.mouse(block: MouseInjectionScope.() -> Unit) {
    runOnUiThread { onRoot().performMouseInput(block) }
    waitForIdle()
  }

  private fun assertBoundsNear(actual: Rect, expected: Rect) {
    assertThat((actual.topLeft - expected.topLeft).getDistance()).isLessThan(1f)
    assertThat((actual.bottomRight - expected.bottomRight).getDistance()).isLessThan(1f)
  }

  private fun ComposeUiTest.bounds(tag: String): Rect = runOnUiThread {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun ComposeUiTest.sampleTransition(
    prefix: String,
    suffix: String,
    scenario: String,
    tabsPixels: PixelMap,
  ): List<Frame> =
    (1..18).map { step ->
      mainClock.advanceTimeByFrame()
      val frame =
        Frame(
          bounds = elements.associateWith { bounds("$prefix$it$suffix") },
          title = textLayout("${prefix}title$suffix"),
        )
      if (step in listOf(4, 10, 16)) {
        assertTabsUnchanged(expected = tabsPixels, actual = saveFrame("$scenario-$step"))
      }
      frame
    }

  private fun ComposeUiTest.textLayout(tag: String): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(
      SemanticsActions.GetTextLayoutResult,
    ) {
      it(layouts)
    }
    return layouts.single()
  }

  private fun ComposeUiTest.assertTabsUnchanged(expected: PixelMap, actual: PixelMap) {
    val tabs = bounds("tabs")
    var changedPixels = 0
    for (y in tabs.top.toInt() + 16 until tabs.bottom.toInt() - 8) {
      for (x in tabs.left.toInt() + 32 until tabs.right.toInt() - 32) {
        if (actual[x, y] != expected[x, y]) {
          changedPixels++
        }
      }
    }
    assertThat(changedPixels, "Shared content must remain behind the navigation tabs").isEqualTo(0)
  }

  private fun ComposeUiTest.saveFrame(name: String): PixelMap {
    val file = File("build/reports/screenshots/episode-navigation-$name.png")
    file.parentFile.mkdirs()
    val image = onRoot().captureToImage()
    ImageIO.write(image.toAwtImage(), "png", file)
    return image.toPixelMap()
  }

  private data class Frame(val bounds: Map<String, Rect>, val title: TextLayoutResult)
}
