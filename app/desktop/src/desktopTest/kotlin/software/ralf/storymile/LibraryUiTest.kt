package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
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
            onNodeWithTag("library-empty").assertIsDisplayed()
            onNodeWithTag("library-illustration", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("library-empty-message", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("library-discover").assertIsDisplayed()
            onNodeWithTag("library-tab-queue").assertIsSelected()
            onNodeWithTag("library-tab-shows").assertIsNotSelected()
            onNodeWithTag("library-tab-saved").assertIsNotSelected()
            listOf(
                "queue" to "Your queue is empty",
                "shows" to "No followed shows",
                "saved" to "No saved episodes",
              )
              .forEach { (tab, title) ->
                runOnUiThread { onNodeWithTag("library-tab-$tab").performClick() }
                onNodeWithTag("library-tab-$tab").assertIsSelected()
                onNodeWithText(title).assertIsDisplayed()
                saveScreenshot("$device-${if (darkTheme) "dark" else "light"}-$tab")
              }
          }
        }
      }
  }

  @Test
  fun `selected library tab survives resizing and discovery stays on the empty screen`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(
      windowSize = DpSize(1440.dp, 1000.dp),
      darkTheme = false,
      contentSize = size,
    ) {
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      runOnUiThread { onNodeWithTag("library-tab-saved").performClick() }
      onNodeWithTag("library-tab-saved").assertIsSelected()

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
          onNodeWithTag("library-tab-saved").assertIsSelected()
          onNodeWithTag("library-tab-shows").assertIsNotSelected()
          onNodeWithTag("library-tab-queue").assertIsNotSelected()
          onNodeWithTag("library-discover").assertIsDisplayed()
          runOnUiThread { onNodeWithTag("library-discover").performClick() }
          onNodeWithTag("library-empty").assertIsDisplayed()
          onNodeWithTag("tab-library").assertIsSelected()
          saveScreenshot("resized-${next.width.value.toInt()}-${next.height.value.toInt()}")
        }
    }
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    runOnUiThread { onNodeWithTag("library-empty").performMouseInput { moveTo(center) } }
    val file = File("build/reports/screenshots/library-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
  }
}
