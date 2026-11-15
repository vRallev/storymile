package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.tabs.TabsPresenter

class LibraryQueueUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `queue shows episodes in enqueue order and opens details across window sizes`() {
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
      listOf("library-queue-reorder-1", "library-queue-remove-0").forEach { control ->
        runOnUiThread { onNodeWithTag(control).performTouchInput { click() } }
        detailsTitle.assertDoesNotExist()
        first.assertIsDisplayed().assertTextContains(firstTitle)
        second.assertIsDisplayed().assertTextContains(secondTitle)
      }
      runOnUiThread {
        onNodeWithTag("library-queue-reorder-1").performTouchInput {
          swipe(start = center, end = center.copy(y = center.y - 50f))
        }
      }
      detailsTitle.assertDoesNotExist()
      first.assertIsDisplayed().assertTextContains(firstTitle)
      second.assertIsDisplayed().assertTextContains(secondTitle)

      runOnIdle { size.value = DesktopWindowSizes.tablet }
      waitUntilAtLeastOneExists(
        matcher = hasTestTag("episode-details-title"),
        timeoutMillis = 5_000,
      )
      first.assertIsSelected()
      second.assertIsNotSelected()
      detailsTitle.assertIsDisplayed().assertTextEquals(firstTitle)
      onNodeWithTag("episode-details-back").assertDoesNotExist()
      assertThat(onNodeWithTag("library-queue-list").getUnclippedBoundsInRoot().right)
        .isLessThanOrEqualTo(
          onNodeWithTag("episode-details-screen").getUnclippedBoundsInRoot().left,
        )

      runOnUiThread { second.performClick() }
      second.assertIsSelected()
      detailsTitle.assertTextEquals(secondTitle)

      runOnUiThread { onNodeWithTag("library-tab-shows").performClick() }
      onNodeWithText("No followed shows").assertIsDisplayed()
      runOnUiThread { onNodeWithTag("library-tab-queue").performClick() }
      second.assertIsSelected()
      detailsTitle.assertIsDisplayed().assertTextEquals(secondTitle)

      runOnIdle { size.value = DesktopWindowSizes.phone }
      detailsTitle.assertIsDisplayed().assertTextEquals(secondTitle)
      onNodeWithTag("library-queue-list").assertDoesNotExist()

      runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
      first.assertIsDisplayed()
      runOnUiThread { first.performClick() }
      detailsTitle.assertIsDisplayed().assertTextEquals(firstTitle)
      onNodeWithText("In queue").assertExists()
      onNodeWithTag("episode-details-add-to-queue").assertDoesNotExist()
    }
  }

  private fun ComposeUiTest.queueEpisode(index: Int, title: String) {
    waitUntilAtLeastOneExists(matcher = hasTestTag("show-detail"), timeoutMillis = 5_000)
    runOnUiThread {
      onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-$index"))
      onNodeWithTag("episode-$index").performClick()
    }
    onNodeWithTag("episode-details-title").assertTextEquals(title)
    runOnUiThread {
      onNodeWithTag("episode-details-add-to-queue").performScrollTo().performClick()
    }
    waitUntilAtLeastOneExists(matcher = hasText("In queue"), timeoutMillis = 5_000)
    runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
  }
}
