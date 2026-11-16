package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.tabs.TabsPresenter

class LibraryQueueUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `saving in Discovery and unsaving in Library keeps the episode queued`() {
    uiTestRule.runPhoneRobotTest {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      val title = "The Quiet Current episode 1"
      openEpisode(index = 0, title = title)
      val queue = hasTestTag("episode-details-add-to-queue")
      waitUntilAtLeastOneExists(matcher = queue and isEnabled(), timeoutMillis = 5_000)
      runOnUiThread { onNode(queue).performScrollTo().performClick() }

      val save = hasTestTag("episode-details-save")
      waitUntilAtLeastOneExists(matcher = save and isEnabled(), timeoutMillis = 5_000)
      runOnUiThread { onNode(save).performScrollTo().performClick() }
      val saved = save and hasContentDescription("Remove from saved episodes")
      waitUntilAtLeastOneExists(matcher = saved, timeoutMillis = 5_000)
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }

      val row = "library-queue-episode-0"
      waitUntilAtLeastOneExists(matcher = hasTestTag(row), timeoutMillis = 5_000)
      runOnUiThread { onNodeWithTag(row).performClick() }
      waitUntilAtLeastOneExists(matcher = saved, timeoutMillis = 5_000)
      runOnUiThread { onNode(saved).performScrollTo().performClick() }
      waitUntilAtLeastOneExists(
        matcher = save and hasContentDescription("Save"),
        timeoutMillis = 5_000,
      )
      onNode(queue).assertTextEquals("In queue")
    }
  }

  @Test
  fun `tapping the Library tab returns from episode details to the queue`() {
    uiTestRule.runPhoneRobotTest {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      val title = "The Quiet Current episode 1"
      queueEpisode(index = 0, title = title)
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }

      val rowTag = "library-queue-episode-0"
      waitUntilAtLeastOneExists(matcher = hasTestTag(rowTag), timeoutMillis = 5_000)
      val row = onNodeWithTag(rowTag)
      val details = onNodeWithTag("episode-details-title")

      runOnUiThread { row.performClick() }
      details.assertIsDisplayed().assertTextEquals(title)
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      row.assertIsDisplayed().assertTextContains(title)
      details.assertDoesNotExist()

      runOnUiThread { row.performClick() }
      details.assertIsDisplayed().assertTextEquals(title)
      composeRobot<AppShellRobot> {
        selectTab(TabsPresenter.Tab.DOWNLOADS)
        selectTab(TabsPresenter.Tab.LIBRARY)
      }
      row.assertIsDisplayed().assertTextContains(title)
      details.assertDoesNotExist()
    }
  }

  @Test
  fun `queue reorders with touch and mouse while preserving episode navigation`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(windowSize = DesktopWindowSizes.tablet, contentSize = size) {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      val firstTitle = "The Quiet Current episode 2"
      val secondTitle = "The Quiet Current episode 1"
      queueEpisode(index = 1, title = firstTitle)
      queueEpisode(index = 0, title = secondTitle)
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-episode-1"),
        timeoutMillis = 5_000,
      )

      val first = onNodeWithTag("library-queue-episode-0")
      val second = onNodeWithTag("library-queue-episode-1")
      val detailsTitle = onNodeWithTag("episode-details-title")
      onNodeWithTag("tab-title").assertTextEquals("Queued episodes")
      onAllNodes(hasText("Queued episodes")).assertCountEquals(1)
      detailsTitle.assertDoesNotExist()
      first.assertIsDisplayed().assertTextContains(firstTitle)
      second.assertIsDisplayed().assertTextContains(secondTitle)

      val touchStart = bounds("library-queue-reorder-1").center
      val touchTarget = bounds("library-queue-reorder-0").center
      runOnUiThread {
        onNodeWithTag("library-queue-reorder-1").performTouchInput {
          swipe(start = center, end = center + (touchTarget - touchStart) * 1.35f)
        }
      }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-episode-0") and hasText(secondTitle),
        timeoutMillis = 5_000,
      )
      detailsTitle.assertDoesNotExist()
      first.assertIsDisplayed().assertTextContains(secondTitle)
      second.assertIsDisplayed().assertTextContains(firstTitle)

      runOnIdle { size.value = DesktopWindowSizes.tablet }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("episode-details-title"),
        timeoutMillis = 5_000,
      )
      first.assertIsSelected()
      second.assertIsNotSelected()
      detailsTitle.assertIsDisplayed().assertTextEquals(secondTitle)
      onNodeWithTag("episode-details-back").assertDoesNotExist()
      assertThat(onNodeWithTag("library-queue-list").getUnclippedBoundsInRoot().right)
        .isLessThanOrEqualTo(
          onNodeWithTag("episode-details-screen").getUnclippedBoundsInRoot().left,
        )

      runOnUiThread { second.performClick() }
      second.assertIsSelected()
      detailsTitle.assertTextEquals(firstTitle)

      val mouseStart = bounds("library-queue-reorder-0").center
      val mouseTarget = bounds("library-queue-reorder-1").center
      runOnUiThread {
        onNodeWithTag("library-queue-reorder-0").performMouseInput {
          moveTo(center)
          press()
          moveTo(center + (mouseTarget - mouseStart) * 1.35f)
          release()
        }
      }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-episode-0") and hasText(firstTitle),
        timeoutMillis = 5_000,
      )
      first.assertIsSelected().assertTextContains(firstTitle)
      second.assertIsNotSelected().assertTextContains(secondTitle)
      detailsTitle.assertTextEquals(firstTitle)

      runOnUiThread { onNodeWithTag("library-tab-shows").performClick() }
      onNodeWithText("No followed shows").assertIsDisplayed()
      runOnUiThread { onNodeWithTag("library-tab-queue").performClick() }
      first.assertIsSelected()
      detailsTitle.assertIsDisplayed().assertTextEquals(firstTitle)

      runOnUiThread { onNodeWithTag("library-queue-reorder-0").performClick() }
      onNodeWithText("Move up").assertIsNotEnabled()
      val moveDown = onNodeWithText("Move down").assertIsEnabled()
      runOnUiThread { moveDown.performClick() }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-episode-1") and hasText(firstTitle),
        timeoutMillis = 5_000,
      )
      second.assertIsSelected()
      detailsTitle.assertIsDisplayed().assertTextEquals(firstTitle)

      runOnIdle { size.value = DesktopWindowSizes.phone }
      detailsTitle.assertIsDisplayed().assertTextEquals(firstTitle)
      onNodeWithTag("library-queue-list").assertDoesNotExist()

      runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
      first.assertIsDisplayed()
      runOnUiThread { first.performClick() }
      detailsTitle.assertIsDisplayed().assertTextEquals(secondTitle)
      val queueButton = onNodeWithTag("episode-details-add-to-queue").assertTextEquals("In queue")
      runOnUiThread { queueButton.performScrollTo().performClick() }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-episode-0") and hasText(firstTitle),
        timeoutMillis = 5_000,
      )
      first.assertIsDisplayed().assertTextContains(firstTitle)
      second.assertDoesNotExist()
      detailsTitle.assertDoesNotExist()
    }
  }

  @Test
  fun `removing queued episodes updates selection and shows the empty state after the last one`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(windowSize = DesktopWindowSizes.tablet, contentSize = size) {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      val firstTitle = "The Quiet Current episode 2"
      val secondTitle = "The Quiet Current episode 1"
      val thirdTitle = "The Quiet Current episode 3"
      queueEpisode(index = 1, title = firstTitle)
      queueEpisode(index = 0, title = secondTitle)
      queueEpisode(index = 2, title = thirdTitle)
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-remove-1"),
        timeoutMillis = 5_000,
      )

      assertSurvivingEpisodeMovesWithoutClipping(tab = "queue", index = 2) {
        runOnUiThread { onNodeWithTag("library-queue-remove-1").performTouchInput { click() } }
      }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-episode-1") and hasText(thirdTitle),
        timeoutMillis = 5_000,
      )
      val first = onNodeWithTag("library-queue-episode-0")
      val second = onNodeWithTag("library-queue-episode-1")
      val detailsTitle = onNodeWithTag("episode-details-title")
      first.assertIsDisplayed().assertTextContains(firstTitle)
      second.assertIsDisplayed().assertTextContains(thirdTitle)
      onNodeWithTag("library-queue-episode-2").assertDoesNotExist()
      onNodeWithText(secondTitle).assertDoesNotExist()
      detailsTitle.assertDoesNotExist()

      runOnIdle { size.value = DesktopWindowSizes.tablet }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("episode-details-title") and hasText(firstTitle),
        timeoutMillis = 5_000,
      )
      first.assertIsSelected()
      runOnUiThread { second.performClick() }
      second.assertIsSelected()
      detailsTitle.assertIsDisplayed().assertTextEquals(thirdTitle)

      runOnUiThread { onNodeWithTag("library-queue-remove-1").performTouchInput { click() } }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("episode-details-title") and hasText(firstTitle),
        timeoutMillis = 5_000,
      )
      first.assertIsDisplayed().assertIsSelected().assertTextContains(firstTitle)
      second.assertDoesNotExist()

      runOnUiThread { first.performClick() }
      detailsTitle.assertIsDisplayed().assertTextEquals(firstTitle)
      assertLastEpisodeFadesBeforeEmptyState(tab = "queue", emptyTitle = "Your queue is empty") {
        runOnUiThread { onNodeWithTag("library-queue-remove-0").performTouchInput { click() } }
      }
      onNodeWithText("Your queue is empty").assertIsDisplayed()
      onNodeWithTag("library-queue-list").assertDoesNotExist()
      detailsTitle.assertDoesNotExist()
    }
  }

  @Test
  fun `dragging near the bottom scrolls to episodes outside the viewport`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(windowSize = DesktopWindowSizes.phone, contentSize = size) {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      val titles = List(3) { "The Quiet Current episode ${it + 1}" }
      titles.forEachIndexed { index, title -> queueEpisode(index = index, title = title) }
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-episode-2") and hasText(titles[2]),
        timeoutMillis = 5_000,
      )
      runOnIdle { size.value = DesktopWindowSizes.phone.copy(height = 400.dp) }
      onNodeWithTag("library-queue-episode-2").assertIsNotDisplayed()
      val start = bounds("library-queue-reorder-0").center
      val list = bounds("library-queue-list")

      mainClock.autoAdvance = false
      try {
        runOnUiThread {
          onRoot().performMouseInput {
            moveTo(start)
            press()
            moveTo(Offset(x = start.x, y = list.bottom - 4f))
          }
        }
        mainClock.advanceTimeBy(1_500)
        runOnUiThread { onRoot().performMouseInput { release() } }
      } finally {
        mainClock.autoAdvance = true
      }

      runOnIdle { size.value = DesktopWindowSizes.phone }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-queue-episode-2") and hasText(titles[0]),
        timeoutMillis = 5_000,
      )
      onNodeWithTag("library-queue-episode-0").assertTextContains(titles[1])
      onNodeWithTag("library-queue-episode-1").assertTextContains(titles[2])
    }
  }

  @Test
  fun `phone queue shares the episode artwork and metadata when opening and closing details`() {
    uiTestRule.runPhoneRobotTest {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      val title = "The Quiet Current episode 1"
      queueEpisode(index = 0, title = title)
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      val row = "library-queue-episode-0"
      waitUntilAtLeastOneExists(matcher = hasTestTag(row), timeoutMillis = 5_000)
      waitForIdle()
      val elements = listOf("artwork", "title", "subtitle", "duration")
      val source = elements.associateWith { bounds("library-queue-$it-0") }

      mainClock.autoAdvance = false
      try {
        runOnUiThread { onNodeWithTag(row).performClick() }
        awaitEntry("episode-details-screen")
        mainClock.advanceTimeBy(96)
        val opening = elements.associateWith { bounds("episode-details-$it") }
        mainClock.advanceTimeBy(1_000)
        val details = elements.associateWith { bounds("episode-details-$it") }
        onNodeWithTag("episode-details-title").assertTextEquals(title)

        runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
        awaitEntry(row)
        mainClock.advanceTimeBy(96)
        val closing = elements.associateWith { bounds("library-queue-$it-0") }
        mainClock.advanceTimeBy(1_000)
        elements.forEach { element ->
          val start = source.getValue(element)
          val end = details.getValue(element)
          assertMovesBetween(
            frame = opening.getValue(element),
            start = start,
            end = end,
            label = "$element opening",
          )
          assertMovesBetween(
            frame = closing.getValue(element),
            start = end,
            end = start,
            label = "$element closing",
          )
          assertThat((bounds("library-queue-$element-0").center - start.center).getDistance())
            .isLessThan(1f)
        }
        listOf(opening, closing).forEach { frame ->
          val width = assertThat(frame.getValue("artwork").width, "artwork resizes")
          width.isGreaterThan(source.getValue("artwork").width)
          width.isLessThan(details.getValue("artwork").width)
        }
        onNodeWithTag(row).assertIsDisplayed()
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

  private fun ComposeUiTest.bounds(tag: String): Rect = runOnUiThread {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun assertMovesBetween(frame: Rect, start: Rect, end: Rect, label: String) {
    val distance = (end.center - start.center).getDistance()
    listOf(start, end).forEach { endpoint ->
      val movement = assertThat((frame.center - endpoint.center).getDistance(), "$label position")
      movement.isGreaterThan(1f)
      movement.isLessThan(distance - 1f)
    }
  }

  private fun ComposeUiTest.queueEpisode(index: Int, title: String) {
    openEpisode(index = index, title = title)
    val queueButton = hasTestTag("episode-details-add-to-queue")
    waitUntilAtLeastOneExists(matcher = queueButton and isEnabled(), timeoutMillis = 5_000)
    runOnUiThread { onNode(queueButton).performScrollTo().performClick() }
    waitUntilAtLeastOneExists(
      matcher = queueButton and hasText("In queue"),
      timeoutMillis = 5_000,
    )
    runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
  }

  private fun ComposeUiTest.openEpisode(index: Int, title: String) {
    waitUntilAtLeastOneExists(matcher = hasTestTag("show-detail"), timeoutMillis = 5_000)
    runOnUiThread {
      onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-$index"))
      onNodeWithTag("episode-$index").performClick()
    }
    onNodeWithTag("episode-details-title").assertTextEquals(title)
  }
}
