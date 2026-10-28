package software.ralf.storymile

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.discovery.ShowRobot

class ShowFollowUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `following and unfollowing keep the catalog and show panes stationary`() {
    listOf(872.dp, 1094.dp, 1280.dp).forEach { width ->
      listOf(false, true).forEach { darkTheme ->
        uiTestRule.runRobotTest(
          windowSize = DpSize(width = width, height = 750.dp),
          darkTheme = darkTheme,
        ) {
          composeRobot<DiscoveryRobot> {
            discover()
            openPodcast("quiet-current")
          }
          composeRobot<ShowRobot> { seeFollowing(false) }
          val bounds =
            listOf(
                "show-list",
                "show-catalog-divider",
                "show-detail",
                "show-artwork",
                "show-title",
                "show-author",
                "show-follow",
                "episode-0",
              )
              .associateWith {
                onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
              }
          mainClock.autoAdvance = false
          try {
            listOf(true, false).forEach { followed ->
              runOnUiThread { onNodeWithTag("show-follow").performClick() }
              waitUntil(timeoutMillis = 5_000) {
                mainClock.advanceTimeByFrame()
                assertPaneBounds(bounds)
                val button = onNodeWithTag("show-follow").fetchSemanticsNode()
                SemanticsProperties.Disabled !in button.config &&
                  button.config[SemanticsProperties.Text].singleOrNull()?.text ==
                    if (followed) "Following" else "Follow"
              }
              repeat(30) {
                mainClock.advanceTimeByFrame()
                assertPaneBounds(bounds)
              }
              mainClock.autoAdvance = true
              composeRobot<ShowRobot> { seeFollowing(followed) }
              mainClock.autoAdvance = false
            }
          } finally {
            mainClock.autoAdvance = true
          }
        }
      }
    }
  }

  private fun ComposeUiTest.assertPaneBounds(bounds: Map<String, Rect>) {
    bounds.forEach { (tag, expected) ->
      val actual =
        onNodeWithTag(tag, useUnmergedTree = true)
          .assertIsDisplayed()
          .fetchSemanticsNode()
          .boundsInRoot
      assertThat(actual, name = tag).isEqualTo(expected)
    }
  }
}
