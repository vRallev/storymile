package software.ralf.storymile

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.tabs.TabsPresenter.Tab

class PlaybackVisibilityUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `adding and removing a Library Queue episode shows and hides playback`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size) {
        composeRobot<AppShellRobot> { seeAppLayers(playbackVisible = false) }
        composeRobot<DiscoveryRobot> {
          discover()
          openPodcast("quiet-current")
        }
        waitUntilAtLeastOneExists(matcher = hasTestTag("show-detail"), timeoutMillis = 5_000)
        runOnUiThread {
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-0"))
          onNodeWithTag("episode-0").performClick()
        }
        val queueButton = hasTestTag("episode-details-add-to-queue")
        waitUntilAtLeastOneExists(matcher = queueButton and isEnabled(), timeoutMillis = 5_000)
        runOnUiThread { onNode(queueButton).performScrollTo().performClick() }

        val title =
          hasTestTag("playback-title-collapsed") and hasText("The Quiet Current episode 1")
        waitUntil("queued episode in playback", timeoutMillis = 5_000) {
          onAllNodes(title, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("playback-bar-play-pause").assertContentDescriptionEquals("Play")
        composeRobot<AppShellRobot> {
          openPlayback()
          collapsePlayback()
          selectTab(Tab.LIBRARY)
        }
        val row = hasTestTag("library-queue-episode-0")
        waitUntilAtLeastOneExists(matcher = row, timeoutMillis = 5_000)
        runOnUiThread { onNode(row).performClick() }
        val remove = queueButton and hasText("In queue")
        waitUntilAtLeastOneExists(matcher = remove, timeoutMillis = 5_000)
        runOnUiThread { onNode(remove).performScrollTo().performClick() }

        waitUntil("playback removed", timeoutMillis = 3_000) {
          onAllNodes(hasTestTag("playback") or hasTestTag("playback-screen"))
            .fetchSemanticsNodes()
            .isEmpty()
        }
        onNodeWithTag("playback").assertDoesNotExist()
        onNodeWithTag("playback-screen").assertDoesNotExist()
      }
    }
  }
}
