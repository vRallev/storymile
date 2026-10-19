package software.ralf.storymile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.discovery.ShowRobot

class DesktopPredictiveBackUiTest {
  @Test
  fun `edge drags preview reverse cancel and complete the show transition`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.desktop).forEach { size ->
      listOf(false, true).forEach { rightEdge ->
        DesktopUiTestRule().runRobotTest(windowSize = size) {
          composeRobot<DiscoveryRobot> {
            discover()
            openPodcast("quiet-current")
          }
          composeRobot<ShowRobot> { seeShow() }
          waitForIdle()
          val original = bounds("show-artwork")
          val root = onRoot().fetchSemanticsNode().boundsInRoot
          val start = if (rightEdge) root.width - 2 else 2f
          val direction = if (rightEdge) -1 else 1
          val distance = minOf(300f, root.width / 2)
          mouse {
            moveTo(Offset(start, root.height / 2))
            press()
            moveTo(Offset(start + distance * 0.7f * direction, root.height / 2))
          }
          onNodeWithTag("show-screen").assertIsDisplayed()
          onNodeWithTag("discovery-screen").assertExists()
          val preview = bounds("show-artwork")
          assertThat((preview.center - original.center).getDistance()).isGreaterThan(1f)

          mouse { moveTo(Offset(start + distance * 0.2f * direction, root.height / 2)) }
          val reversed = bounds("show-artwork")
          assertThat((reversed.center - original.center).getDistance())
            .isLessThan((preview.center - original.center).getDistance())
          mouse { release() }
          onNodeWithTag("show-screen").assertIsDisplayed()
          onNodeWithTag("discovery-screen").assertDoesNotExist()
          assertThat((bounds("show-artwork").center - original.center).getDistance()).isLessThan(1f)

          mouse {
            moveTo(Offset(start, root.height / 2))
            press()
            moveTo(Offset(start + distance * 0.7f * direction, root.height / 2))
          }
          mouse { release() }
          waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag("show-screen")).fetchSemanticsNodes().isEmpty()
          }
          onNodeWithTag("discovery-screen").assertIsDisplayed()
        }
      }
    }
  }

  private fun ComposeUiTest.mouse(block: MouseInjectionScope.() -> Unit) {
    runOnUiThread { onRoot().performMouseInput(block) }
    waitForIdle()
  }

  private fun ComposeUiTest.bounds(tag: String): Rect = runOnUiThread {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }
}
