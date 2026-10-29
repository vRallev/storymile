package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.discovery.ShowRobot
import software.ralf.storymile.tabs.TabsPresenter

class LibraryShowsUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `followed shows render from discovery in both themes and survive window resizing`() {
    listOf(false, true).forEach { darkTheme ->
      val size = mutableStateOf(DesktopWindowSizes.phone)
      uiTestRule.runRobotTest(
        windowSize = DpSize(1440.dp, 1000.dp),
        contentSize = size,
        darkTheme = darkTheme,
      ) {
        composeRobot<DiscoveryRobot> { discover() }
        listOf("quiet-current", "field-notes", "small-wonders").forEach { id ->
          composeRobot<DiscoveryRobot> { openPodcast(id) }
          composeRobot<ShowRobot> {
            follow()
            back()
          }
        }
        composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
        runOnUiThread { onNodeWithTag("library-tab-shows").performClick() }
        onNodeWithTag("library-tab-shows").assertIsSelected()
        onNodeWithText("Followed shows").assertIsDisplayed()
        onNodeWithText("No followed shows").assertDoesNotExist()
        onNodeWithText("The Quiet Current").assertIsDisplayed()
        onNodeWithText("Small Wonders").assertIsDisplayed()
        saveScreenshot("phone-${if (darkTheme) "dark" else "light"}")
        runOnUiThread {
          onNodeWithTag("discovery-podcast-https://feeds.test/quiet-current").performClick()
        }
        onNodeWithTag("show-title", useUnmergedTree = true).assertTextEquals("The Quiet Current")
        listOf(
            "tablet" to DesktopWindowSizes.tablet,
            "desktop" to DesktopWindowSizes.desktop,
            "short-wide" to DpSize(1100.dp, 420.dp),
            "tall-narrow" to DpSize(360.dp, 1000.dp),
            "phone-return" to DesktopWindowSizes.phone,
          )
          .forEach { (name, nextSize) ->
            runOnIdle { size.value = nextSize }
            onNodeWithTag("library-tab-shows").assertIsSelected()
            onNodeWithTag("show-title", useUnmergedTree = true)
              .assertTextEquals("The Quiet Current")
            saveScreenshot("$name-${if (darkTheme) "dark" else "light"}")
          }
        runOnUiThread {
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-0"))
          onNodeWithTag("episode-0").performClick()
        }
        onNodeWithTag("episode-details-screen").assertIsDisplayed()
        runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
        onNodeWithTag("library-tab-shows").assertIsSelected()
        onNodeWithTag("show-title", useUnmergedTree = true).assertTextEquals("The Quiet Current")
        runOnUiThread { onNodeWithTag("library-show-back").performClick() }
        onNodeWithTag("library-shows-list").assertIsDisplayed()
        composeRobot<AppShellRobot> {
          selectTab(TabsPresenter.Tab.HOME)
          selectTab(TabsPresenter.Tab.LIBRARY)
        }
        onNodeWithTag("library-tab-shows").assertIsSelected()
        onNodeWithText("The Quiet Current").assertIsDisplayed()
        // Dismiss the navigation tooltip before resizing replaces the bottom bar.
        runOnUiThread { onNodeWithTag("library-pager").performMouseInput { moveTo(center) } }
        runOnIdle { size.value = DesktopWindowSizes.desktop }
        onNodeWithTag("show-detail").performScrollToNode(hasTestTag("show-title"))
        onNodeWithTag("show-title", useUnmergedTree = true).assertTextEquals("The Quiet Current")
      }
    }
  }

  @Test
  fun `unfollowing the last show restores the empty state and discovery action`() {
    uiTestRule.runPhoneRobotTest(darkTheme = false) {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      composeRobot<ShowRobot> { follow() }
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      runOnUiThread { onNodeWithTag("library-tab-shows").performClick() }
      onNodeWithTag("library-shows-list").assertIsDisplayed()
      runOnUiThread {
        onNodeWithTag("discovery-podcast-https://feeds.test/quiet-current").performClick()
      }
      onNodeWithTag("library-show-details").assertIsDisplayed()
      runOnUiThread { onNodeWithTag("show-detail").performScrollToNode(hasTestTag("show-follow")) }
      runOnUiThread { onNodeWithTag("show-follow").performClick() }
      waitUntilCatching("Empty shows state displayed") {
        onNodeWithText("No followed shows").assertIsDisplayed()
      }
      onNodeWithTag("library-discover").assertIsDisplayed()
    }
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    val file = File("build/reports/screenshots/library-shows-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
  }
}
