package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.tabs.TabsPresenter

class LibrarySavedUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `saved episodes show newest first and removing a selected episode updates the detail pane`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(windowSize = DesktopWindowSizes.tablet, contentSize = size) {
      val titles = List(3) { "The Quiet Current episode ${it + 1}" }
      saveEpisodesInDiscovery(1, 0, 2)

      val first = onNodeWithTag("library-saved-episode-0")
      val second = onNodeWithTag("library-saved-episode-1")
      val third = onNodeWithTag("library-saved-episode-2")
      val details = onNodeWithTag("episode-details-title")
      onNodeWithTag("tab-title").assertTextEquals("Saved episodes")
      first.assertIsDisplayed().assertTextContains(titles[2])
      second.assertIsDisplayed().assertTextContains(titles[0])
      third.assertIsDisplayed().assertTextContains(titles[1])
      titles.forEach { onNodeWithContentDescription("Reorder $it").assertDoesNotExist() }

      assertSurvivingEpisodeMovesWithoutClipping(tab = "saved", index = 2) {
        removeEpisode(titles[0])
      }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("library-saved-episode-1") and hasText(titles[1]),
        timeoutMillis = 5_000,
      )
      first.assertTextContains(titles[2])
      third.assertDoesNotExist()
      details.assertDoesNotExist()

      runOnIdle { size.value = DesktopWindowSizes.tablet }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("episode-details-title") and hasText(titles[2]),
        timeoutMillis = 5_000,
      )
      first.assertIsSelected()
      second.assertIsNotSelected()
      onNodeWithTag("episode-details-back").assertDoesNotExist()
      assertThat(onNodeWithTag("library-saved-list").getUnclippedBoundsInRoot().right)
        .isLessThanOrEqualTo(
          onNodeWithTag("episode-details-screen").getUnclippedBoundsInRoot().left,
        )

      runOnUiThread { second.performClick() }
      second.assertIsSelected()
      details.assertIsDisplayed().assertTextEquals(titles[1])
      removeEpisode(titles[1])
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("episode-details-title") and hasText(titles[2]),
        timeoutMillis = 5_000,
      )
      first.assertIsSelected()
      second.assertDoesNotExist()

      assertLastEpisodeFadesBeforeEmptyState(tab = "saved", emptyTitle = "No saved episodes") {
        removeEpisode(titles[2])
      }
      onNodeWithText("No saved episodes").assertIsDisplayed()
      details.assertDoesNotExist()
    }
  }

  @Test
  fun `phone saved list shares episode artwork and title when opening and closing details`() {
    uiTestRule.runPhoneRobotTest {
      saveEpisodesInDiscovery(0)
      val row = "library-saved-episode-0"
      val elements = listOf("artwork", "title")
      val listBounds = elements.associateWith { transitionBounds("library-saved-$it-0") }

      mainClock.autoAdvance = false
      try {
        runOnUiThread { onNodeWithTag(row).performClick() }
        awaitTransitionEntry("episode-details-screen")
        val opening = elements.associateWith { transitionBounds("episode-details-$it") }
        mainClock.advanceTimeBy(1_000)
        val detailBounds = elements.associateWith { transitionBounds("episode-details-$it") }
        onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 1")

        runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
        awaitTransitionEntry(row)
        val closing = elements.associateWith { transitionBounds("library-saved-$it-0") }
        mainClock.advanceTimeBy(1_000)

        elements.forEach { element ->
          val list = listBounds.getValue(element)
          val details = detailBounds.getValue(element)
          val distance = (details.center - list.center).getDistance()
          listOf(opening.getValue(element), closing.getValue(element)).forEach { frame ->
            listOf(list, details).forEach { endpoint ->
              val movement = (frame.center - endpoint.center).getDistance()
              assertThat(movement, "$element moves between endpoints").isGreaterThan(1f)
              assertThat(movement, "$element moves between endpoints").isLessThan(distance - 1f)
            }
          }
        }
        listOf(opening, closing).forEach { frame ->
          assertThat(frame.getValue("artwork").width)
            .isGreaterThan(listBounds.getValue("artwork").width)
          assertThat(frame.getValue("artwork").width)
            .isLessThan(detailBounds.getValue("artwork").width)
        }
        onNodeWithTag(row).assertIsDisplayed()
        onNodeWithTag("episode-details-screen").assertDoesNotExist()
      } finally {
        mainClock.autoAdvance = true
      }
    }
  }

  private fun ComposeUiTest.saveEpisodesInDiscovery(vararg indices: Int) {
    composeRobot<DiscoveryRobot> {
      discover()
      openPodcast("quiet-current")
    }
    val save = hasTestTag("episode-details-save")
    indices.forEach { index ->
      waitUntilAtLeastOneExists(matcher = hasTestTag("show-detail"), timeoutMillis = 5_000)
      runOnUiThread {
        onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-$index"))
        onNodeWithTag("episode-$index").performClick()
      }
      waitUntilAtLeastOneExists(matcher = save and isEnabled(), timeoutMillis = 5_000)
      runOnUiThread { onNode(save).performScrollTo().performClick() }
      waitUntilAtLeastOneExists(
        matcher = save and hasContentDescription("Remove from saved episodes"),
        timeoutMillis = 5_000,
      )
      runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
      waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasTestTag("episode-details-screen")).fetchSemanticsNodes().isEmpty()
      }
    }
    composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
    runOnUiThread { onNodeWithTag("library-tab-saved").performClick() }
    waitUntilAtLeastOneExists(
      matcher = hasTestTag("library-saved-episode-${indices.lastIndex}"),
      timeoutMillis = 5_000,
    )
    waitForIdle()
  }

  private fun ComposeUiTest.removeEpisode(title: String) {
    runOnUiThread {
      onNodeWithContentDescription("Remove $title from saved episodes").performTouchInput {
        click()
      }
    }
  }

  private fun ComposeUiTest.awaitTransitionEntry(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
      mainClock.advanceTimeByFrame()
      onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeBy(96)
  }

  private fun ComposeUiTest.transitionBounds(tag: String): Rect = runOnUiThread {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }
}
