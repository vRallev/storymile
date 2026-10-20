package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.discovery.ShowRobot

class ShowEpisodePaneUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `small tablet panes keep podcast actions and episode text readable`() {
    listOf(DpSize(600.dp, 960.dp) to 1f, DpSize(800.dp, 1280.dp) to 1.5f).forEach {
      (size, fontScale) ->
      uiTestRule.runRobotTest(windowSize = size, fontScale = fontScale) {
        openShow()
        assertSingleLineFollow()
        scrollToEpisode(0)
        runOnUiThread { onNodeWithTag("episode-0").performClick() }
        waitForIdle()
        onNodeWithTag("episode-details-screen").assertIsDisplayed()
        scrollToEpisode(0)
        val episode = bounds("episode-0")
        val title = bounds("episode-title-0")
        assertThat(title.width).isGreaterThan(episode.width * 0.5f)
        runOnUiThread {
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("show-follow"))
        }
        assertSingleLineFollow()
        saveFrame("small-tablet-${size.width.value.toInt()}-$fontScale")
      }
    }
  }

  @Test
  fun `wide episode navigation shrinks the podcast into the narrow left pane`() {
    listOf(872.dp, 1280.dp).forEach { width ->
      uiTestRule.runRobotTest(windowSize = DpSize(width = width, height = 800.dp)) {
        openShow()
        scrollToEpisode(0)
        val initial = frame()
        val catalog = requireNotNull(initial.catalog)
        val episode = bounds("episode-0")
        assertCompactPodcast(initial)
        assertSingleLineFollow()
        saveFrame("${width.value.toInt()}-catalog")

        mainClock.autoAdvance = false
        try {
          runOnUiThread { onNodeWithTag("episode-0").performClick() }
          awaitEntry("episode-details-screen")
          val forward = sampleTransition("${width.value.toInt()}-forward")
          mainClock.advanceTimeBy(1_000)
          val selected = frame()
          val details = bounds("episode-details-screen")
          saveFrame("${width.value.toInt()}-details")
          assertCompactPodcast(selected)
          assertSingleLineFollow()
          assertBoundsNear(actual = selected.podcast.left, expected = catalog.left)
          assertBoundsNear(actual = selected.podcast.width, expected = catalog.width)
          assertThat(selected.podcast.right).isLessThanOrEqualTo(details.left)
          assertBoundsNear(actual = details.right, expected = initial.podcast.right)
          assertBoundsNear(actual = details.width, expected = initial.podcast.width)
          assertThat(
              forward.any {
                it.podcast.left > selected.podcast.left + 1f &&
                  it.podcast.left < initial.podcast.left - 1f
              },
            )
            .isTrue()
          assertThat(
              forward.any {
                it.catalog != null &&
                  it.catalog.left < catalog.left - 1f &&
                  it.catalog.right > catalog.left + 1f
              },
            )
            .isTrue()
          assertPodcastShrinks(listOf(initial) + forward + selected)
          forward.forEach { assertCompactPodcast(it) }
          assertDividersMove(forward)
          onNodeWithTag("show-list").assertDoesNotExist()
          onNodeWithTag("show-detail").assertIsDisplayed()
          onNodeWithTag("episode-details-back").assertDoesNotExist()
          onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 1")

          runOnUiThread { onNodeWithTag("show-back").performClick() }
          awaitEntry("show-list")
          val backward = sampleTransition("${width.value.toInt()}-back")
          mainClock.advanceTimeBy(1_000)
          assertThat(
              backward.any {
                it.podcast.left > selected.podcast.left + 1f &&
                  it.podcast.left < initial.podcast.left - 1f
              },
            )
            .isTrue()
          val restored = frame()
          assertPodcastShrinks((listOf(selected) + backward + restored).reversed())
          backward.forEach { assertCompactPodcast(it) }
          assertDividersMove(backward)
          assertSamePodcast(actual = restored, expected = initial)
          assertBoundsNear(actual = bounds("show-list"), expected = catalog)
          assertBoundsNear(actual = bounds("show-detail"), expected = initial.podcast)
          assertBoundsNear(actual = bounds("episode-0"), expected = episode)
          onNodeWithTag("episode-details-screen").assertDoesNotExist()
          saveFrame("${width.value.toInt()}-restored")
        } finally {
          mainClock.autoAdvance = true
        }
      }
    }
  }

  @Test
  fun `selected episode and expanded description survive resizing and one back closes details`() {
    val size = mutableStateOf(DpSize(width = 1280.dp, height = 800.dp))
    uiTestRule.runRobotTest(
      windowSize = DpSize(width = 1440.dp, height = 1000.dp),
      darkTheme = false,
      contentSize = size,
    ) {
      openShow()
      runOnUiThread {
        onNodeWithTag("show-detail").performScrollToNode(hasTestTag("show-info"))
        onNodeWithTag("show-info").performClick()
      }
      waitForIdle()
      onNodeWithTag("show-description").assertIsDisplayed()
      scrollToEpisode(0)
      runOnUiThread { onNodeWithTag("episode-0").performClick() }
      waitForIdle()
      onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 1")
      scrollToEpisode(1)
      runOnUiThread { onNodeWithTag("episode-1").performClick() }
      waitForIdle()
      onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 2")

      runOnIdle { size.value = DpSize(width = 412.dp, height = 916.dp) }
      waitForIdle()
      saveFrame("resized-phone")
      onNodeWithTag("show-detail").assertDoesNotExist()
      onNodeWithTag("show-list").assertDoesNotExist()
      onNodeWithTag("episode-details-back").assertIsDisplayed()
      onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 2")

      runOnIdle { size.value = DpSize(width = 1280.dp, height = 800.dp) }
      waitForIdle()
      saveFrame("resized-wide")
      onNodeWithTag("show-detail").assertIsDisplayed()
      onNodeWithTag("show-list").assertDoesNotExist()
      onNodeWithTag("episode-details-back").assertDoesNotExist()
      onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 2")
      assertThat(bounds("show-detail").right)
        .isLessThanOrEqualTo(bounds("episode-details-screen").left)

      runOnUiThread { onNodeWithTag("show-back").performClick() }
      waitForIdle()
      onNodeWithTag("episode-details-screen").assertDoesNotExist()
      onNodeWithTag("show-list").assertIsDisplayed()
      runOnUiThread {
        onNodeWithTag("show-detail").performScrollToNode(hasTestTag("show-description"))
      }
      onNodeWithTag("show-description").assertIsDisplayed()
      composeRobot<ShowRobot> { back() }
      onNodeWithTag("discovery-screen").assertIsDisplayed()
    }
  }

  @Test
  fun `wide episode back preview reverses cancels and restores the catalog`() {
    uiTestRule.runRobotTest(windowSize = DpSize(width = 1280.dp, height = 800.dp)) {
      openShow()
      scrollToEpisode(0)
      val initial = frame()
      runOnUiThread { onNodeWithTag("episode-0").performClick() }
      waitForIdle()
      val selected = frame()
      val root = onRoot().fetchSemanticsNode().boundsInRoot
      val distance = minOf(300f, root.width / 2)

      // A held predictive gesture does not become idle; advance its frames explicitly.
      mainClock.autoAdvance = false
      try {
        mouse {
          moveTo(Offset(x = 2f, y = root.height / 2))
          press()
          moveTo(Offset(x = 2f + distance * 0.7f, y = root.height / 2))
        }
        awaitEntry("show-list")
        onNodeWithTag("show-list").assertExists()
        val preview = frame()
        assertThat(preview.podcast.left).isGreaterThan(selected.podcast.left + 1f)
        assertThat(preview.podcast.width).isGreaterThan(selected.podcast.width + 1f)
        assertThat(preview.podcast.width).isLessThan(initial.podcast.width)
        assertCompactPodcast(preview)
        assertThat(requireNotNull(preview.episodeDivider).left)
          .isGreaterThan(requireNotNull(selected.episodeDivider).left + 1f)
        saveFrame("back-preview")
        mouse { moveTo(Offset(x = 2f + distance * 0.2f, y = root.height / 2)) }
        val reversed = frame()
        assertThat(reversed.podcast.left).isLessThan(preview.podcast.left)
        assertThat(reversed.podcast.width).isLessThan(preview.podcast.width)
        assertThat(reversed.podcast.width).isGreaterThan(selected.podcast.width)
        assertCompactPodcast(reversed)
        assertThat(requireNotNull(reversed.episodeDivider).left)
          .isLessThan(requireNotNull(preview.episodeDivider).left)
        assertThat(requireNotNull(reversed.catalogDivider).left)
          .isLessThan(requireNotNull(preview.catalogDivider).left)
        saveFrame("back-reversed")
        mouse { release() }
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("show-list").assertDoesNotExist()
        onNodeWithTag("episode-details-screen").assertIsDisplayed()
        assertBoundsNear(actual = bounds("show-detail"), expected = selected.podcast)
        assertSamePodcast(actual = frame(), expected = selected)
        saveFrame("back-cancelled")

        mouse {
          moveTo(Offset(x = 2f, y = root.height / 2))
          press()
          moveTo(Offset(x = 2f + distance * 0.7f, y = root.height / 2))
        }
        mouse { release() }
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("episode-details-screen").assertDoesNotExist()
        onNodeWithTag("show-list").assertIsDisplayed()
        onNodeWithTag("show-detail").assertIsDisplayed()
        onNodeWithTag("episode-0").assertIsDisplayed()
        assertSamePodcast(actual = frame(), expected = initial)
      } finally {
        mainClock.autoAdvance = true
      }
    }
  }

  private fun ComposeUiTest.openShow() {
    composeRobot<DiscoveryRobot> {
      discover()
      openPodcast("quiet-current")
    }
    composeRobot<ShowRobot> { pressPreviewActions() }
    waitForIdle()
  }

  private fun ComposeUiTest.scrollToEpisode(index: Int) {
    runOnUiThread {
      onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-$index"))
    }
    waitForIdle()
  }

  private fun ComposeUiTest.awaitEntry(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
      mainClock.advanceTimeByFrame()
      onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
    mainClock.advanceTimeByFrame()
  }

  private fun ComposeUiTest.sampleTransition(name: String): List<Frame> =
    (1..30).map { step ->
      mainClock.advanceTimeByFrame()
      val frame = frame()
      if (step in listOf(4, 10, 16)) {
        saveFrame("$name-$step")
      }
      frame
    }

  private fun ComposeUiTest.frame(): Frame =
    Frame(
      podcast = bounds("show-detail"),
      catalog = optionalBounds("show-list"),
      artwork = bounds("show-artwork"),
      title = bounds("show-title"),
      follow = bounds("show-follow"),
      catalogDivider = optionalBounds("show-catalog-divider"),
      episodeDivider = optionalBounds("show-episode-divider"),
    )

  private fun assertSamePodcast(actual: Frame, expected: Frame) {
    assertBoundsNear(actual = actual.podcast, expected = expected.podcast)
    assertBoundsNear(
      actual = actual.artwork.translate(-actual.podcast.topLeft),
      expected = expected.artwork.translate(-expected.podcast.topLeft),
    )
    assertBoundsNear(
      actual = actual.title.translate(-actual.podcast.topLeft),
      expected = expected.title.translate(-expected.podcast.topLeft),
    )
    assertBoundsNear(
      actual = actual.follow.translate(-actual.podcast.topLeft),
      expected = expected.follow.translate(-expected.podcast.topLeft),
    )
    assertCompactPodcast(actual)
  }

  private fun assertCompactPodcast(actual: Frame) {
    assertThat(actual.artwork.width).isLessThanOrEqualTo(240f)
    assertThat(actual.title.left).isGreaterThan(actual.artwork.right)
    assertThat(actual.follow.left).isGreaterThan(actual.artwork.right)
    assertThat(actual.title.right).isLessThanOrEqualTo(actual.podcast.right + 1f)
    assertThat(actual.follow.right).isLessThanOrEqualTo(actual.podcast.right + 1f)
    actual.catalogDivider?.let {
      assertBoundsNear(actual = it.right, expected = actual.podcast.left - 24f)
      assertBoundsNear(actual = it.width, expected = 1f)
    }
    actual.episodeDivider?.let {
      assertBoundsNear(actual = it.left, expected = actual.podcast.right + 24f)
      assertBoundsNear(actual = it.width, expected = 1f)
    }
  }

  private fun assertPodcastShrinks(frames: List<Frame>) {
    val initial = frames.first()
    val selected = frames.last()
    val widthChange = initial.podcast.width - selected.podcast.width
    assertThat(widthChange).isGreaterThan(1f)
    assertThat(
        frames.any {
          it.podcast.width > selected.podcast.width + 1f &&
            it.podcast.width < initial.podcast.width - 1f
        },
      )
      .isTrue()
    frames.zipWithNext { previous, next ->
      assertThat(next.podcast.width).isLessThanOrEqualTo(previous.podcast.width + 1f)
      assertThat(previous.podcast.width - next.podcast.width)
        .isLessThanOrEqualTo(widthChange * 0.25f + 1f)
      assertThat(next.artwork.width).isLessThanOrEqualTo(previous.artwork.width + 1f)
    }
  }

  private fun ComposeUiTest.assertSingleLineFollow() {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithText("Follow", useUnmergedTree = true).performSemanticsAction(
      SemanticsActions.GetTextLayoutResult,
    ) {
      it(layouts)
    }
    assertThat(layouts.single().lineCount).isEqualTo(1)
  }

  private fun assertDividersMove(frames: List<Frame>) {
    val catalogDividers = frames.mapNotNull { it.catalogDivider }
    val episodeDividers = frames.mapNotNull { it.episodeDivider }
    assertThat(catalogDividers.maxOf { it.left } - catalogDividers.minOf { it.left })
      .isGreaterThan(1f)
    assertThat(episodeDividers.maxOf { it.left } - episodeDividers.minOf { it.left })
      .isGreaterThan(1f)
  }

  private fun ComposeUiTest.mouse(block: MouseInjectionScope.() -> Unit) {
    runOnUiThread { onRoot().performMouseInput(block) }
    repeat(4) { mainClock.advanceTimeByFrame() }
  }

  private fun ComposeUiTest.bounds(tag: String): Rect = runOnUiThread {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun ComposeUiTest.optionalBounds(tag: String): Rect? =
    if (onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
      null
    } else {
      bounds(tag)
    }

  private fun assertBoundsNear(actual: Rect, expected: Rect) {
    assertThat((actual.topLeft - expected.topLeft).getDistance()).isLessThan(1f)
    assertThat((actual.bottomRight - expected.bottomRight).getDistance()).isLessThan(1f)
  }

  private fun assertBoundsNear(actual: Float, expected: Float) {
    assertThat(kotlin.math.abs(actual - expected)).isLessThanOrEqualTo(1f)
  }

  private fun ComposeUiTest.saveFrame(name: String) {
    val file = File("build/reports/screenshots/show-episode-pane-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
  }

  private data class Frame(
    val podcast: Rect,
    val catalog: Rect?,
    val artwork: Rect,
    val title: Rect,
    val follow: Rect,
    val catalogDivider: Rect?,
    val episodeDivider: Rect?,
  )
}
