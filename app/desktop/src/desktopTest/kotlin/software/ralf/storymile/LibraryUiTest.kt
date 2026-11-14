package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.discovery.ShowRobot
import software.ralf.storymile.tabs.TabsPresenter

class LibraryUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `library renders in the production graph on phone tablet and desktop in both themes`() {
    listOf(
        "phone" to DesktopWindowSizes.phone,
        "tablet" to DesktopWindowSizes.tablet,
        "desktop" to DesktopWindowSizes.desktop,
      )
      .forEach { (device, size) ->
        listOf(false, true).forEach { darkTheme ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
            composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
            onNodeWithTag("tab-library").assertIsSelected()
            onNodeWithTag("library-screen").assertIsDisplayed()
            onNodeWithTag("library-illustration", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("library-empty-message", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("library-discover").assertIsDisplayed()
            onNodeWithTag("library-tab-queue").assertIsSelected()
            onNodeWithTag("library-tab-shows").assertIsNotSelected()
            onNodeWithTag("library-tab-saved").assertIsNotSelected()
            onNodeWithTag("library-tab-history").assertIsNotSelected()
            listOf(
                "queue" to "Your queue is empty",
                "shows" to "No followed shows",
                "saved" to "No saved episodes",
                "history" to "No listening history yet",
              )
              .forEach { (tab, title) ->
                runOnUiThread { onNodeWithTag("library-tab-$tab").performClick() }
                onNodeWithTag("library-tab-$tab").assertIsSelected()
                waitUntil(timeoutMillis = 5_000) {
                  onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText(title).assertIsDisplayed()
                saveScreenshot("$device-${if (darkTheme) "dark" else "light"}-$tab")
              }
          }
        }
      }
  }

  @Test
  fun `followed shows open from the library and keep their selection across layouts`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(
      windowSize = DpSize(width = 1440.dp, height = 1000.dp),
      darkTheme = false,
      contentSize = size,
    ) {
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      runOnUiThread { onNodeWithTag("library-tab-shows").performClick() }
      waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag("library-discover").fetchSemanticsNodes().size == 1
      }
      runOnUiThread { onNodeWithTag("library-discover").performClick() }
      composeRobot<DiscoveryRobot> { openPodcast("quiet-current") }
      composeRobot<ShowRobot> {
        follow()
        back()
      }
      composeRobot<DiscoveryRobot> { openPodcast("field-notes") }
      composeRobot<ShowRobot> {
        follow()
        back()
      }
      composeRobot<DiscoveryRobot> { backFromCatalog() }

      val quietCurrent = "library-show-https://feeds.test/quiet-current"
      val fieldNotes = "library-show-https://feeds.test/field-notes"
      onNodeWithTag("library-tab-shows").assertIsSelected()
      onNodeWithTag(quietCurrent).assertIsDisplayed()
      onNodeWithTag(fieldNotes).assertIsDisplayed()
      onAllNodesWithTag("library-show-detail").assertCountEquals(0)

      val portraitTablet = DpSize(width = 800.dp, height = 1000.dp)
      runOnIdle { size.value = portraitTablet }
      onNodeWithTag("library-shows-grid").assertIsDisplayed()
      runOnUiThread { onNodeWithTag(quietCurrent).performClick() }
      onNodeWithTag("library-show-back").assertIsDisplayed()
      onNodeWithText("The Quiet Current").assertIsDisplayed()

      runOnIdle { size.value = DpSize(width = 900.dp, height = 360.dp) }
      onNodeWithTag("library-shows-list").assertIsDisplayed()
      onNodeWithTag(quietCurrent).assertIsSelected()

      runOnIdle { size.value = DesktopWindowSizes.desktop }
      onNodeWithTag("library-shows-list").assertIsDisplayed()
      onNodeWithTag(quietCurrent).assertIsSelected()
      val firstEpisode = "library-show-episode-0"
      runOnUiThread {
        onNodeWithTag("library-show-detail").performScrollToNode(hasTestTag(firstEpisode))
        onNodeWithTag(firstEpisode).performClick()
      }
      onNodeWithTag("episode-details-title").assertIsDisplayed()
      runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
      onNodeWithTag(quietCurrent).assertIsSelected()

      runOnIdle { size.value = DesktopWindowSizes.phone }
      onNodeWithTag("library-show-back").assertIsDisplayed()
      runOnUiThread {
        onNodeWithTag("library-show-detail").performScrollToNode(hasTestTag("library-show-actions"))
        onNodeWithTag("library-show-actions").performClick()
        onNodeWithText("Unfollow").performClick()
      }
      waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(quietCurrent).fetchSemanticsNodes().isEmpty() &&
          onAllNodesWithTag(fieldNotes).fetchSemanticsNodes().size == 1
      }
      onNodeWithTag("library-tab-shows").assertIsSelected()
    }
  }

  @Test
  fun `swiped library tab survives resizing`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(
      windowSize = DpSize(1440.dp, 1000.dp),
      darkTheme = false,
      contentSize = size,
    ) {
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      runOnUiThread { onNodeWithTag("library-pager").performTouchInput { swipeLeft() } }
      onNodeWithTag("library-tab-shows").assertIsSelected()
      runOnUiThread { onNodeWithTag("library-pager").performTouchInput { swipeLeft() } }
      onNodeWithTag("library-tab-saved").assertIsSelected()
      runOnUiThread { onNodeWithTag("library-pager").performTouchInput { swipeLeft() } }
      onNodeWithTag("library-tab-history").assertIsSelected()

      listOf(
          DesktopWindowSizes.tablet,
          DesktopWindowSizes.desktop,
          DpSize(900.dp, 360.dp),
          DpSize(320.dp, 1000.dp),
          DesktopWindowSizes.phone,
        )
        .forEach { next ->
          runOnIdle { size.value = next }
          onNodeWithTag("tab-library").assertIsSelected()
          onNodeWithTag("library-tab-history").assertIsSelected()
          onNodeWithTag("library-tab-saved").assertIsNotSelected()
          onNodeWithTag("library-tab-shows").assertIsNotSelected()
          onNodeWithTag("library-tab-queue").assertIsNotSelected()
          onNodeWithTag("library-discover").assertIsDisplayed()
          saveScreenshot("resized-${next.width.value.toInt()}-${next.height.value.toInt()}")
        }
    }
  }

  @Test
  fun `every empty library tab opens discovery and back returns to the selected tab`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DesktopWindowSizes.desktop)
      .forEach { size ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
          composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
          listOf("queue", "shows", "saved", "history").forEach { tab ->
            runOnUiThread { onNodeWithTag("library-tab-$tab").performClick() }
            onNodeWithTag("library-tab-$tab").assertIsSelected()
            runOnUiThread { onNodeWithTag("library-discover").performClick() }

            composeRobot<DiscoveryRobot> {
              seePodcast("quiet-current")
              openPodcast("quiet-current")
            }
            onNodeWithTag("tab-library").assertIsSelected()
            composeRobot<ShowRobot> {
              seeShow()
              back()
            }
            composeRobot<DiscoveryRobot> { backFromCatalog() }
            onNodeWithTag("library-screen").assertIsDisplayed()
            onNodeWithTag("library-tab-$tab").assertIsSelected()
            onNodeWithTag("tab-library").assertIsSelected()
          }
          composeRobot<AppShellRobot> {
            selectTab(TabsPresenter.Tab.HOME)
            selectTab(TabsPresenter.Tab.LIBRARY)
          }
          onNodeWithTag("library-tab-history").assertIsSelected()
        }
      }
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    runOnUiThread { onNodeWithTag("library-screen").performMouseInput { moveTo(center) } }
    val file = File("build/reports/screenshots/library-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
  }
}
