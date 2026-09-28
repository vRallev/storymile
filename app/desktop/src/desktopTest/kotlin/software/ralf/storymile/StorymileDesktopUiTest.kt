@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching

class StorymileDesktopUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `phone shows empty content playback and bottom tabs`() {
    listOf(false, true).forEach { darkTheme ->
      uiTestRule.runPhoneRobotTest(darkTheme = darkTheme) {
        waitUntilCatching("compact app containers displayed", timeout = 3.seconds) {
          composeRobot<AppShellRobot> { seeCompactLayout() }
        }
        saveScreenshot("phone-${if (darkTheme) "dark" else "light"}")
      }
    }
  }

  @Test
  fun `tablet shows empty content side tabs and full width playback`() {
    listOf(false, true).forEach { darkTheme ->
      uiTestRule.runTabletRobotTest(darkTheme = darkTheme) {
        waitUntilCatching("expanded app containers displayed", timeout = 3.seconds) {
          composeRobot<AppShellRobot> { seeExpandedLayout() }
        }
        saveScreenshot("tablet-${if (darkTheme) "dark" else "light"}")
      }
    }
  }

  @Test
  fun `sidebar expansion follows width instead of height`() {
    listOf(
        DpSize(1440.dp, 900.dp) to 280.dp,
        DpSize(1199.dp, 900.dp) to 96.dp,
        DpSize(1200.dp, 900.dp) to 280.dp,
        DpSize(1200.dp, 500.dp) to 280.dp,
        DpSize(900.dp, 1440.dp) to 96.dp,
      )
      .forEach { (size, tabWidth) ->
        listOf(false, true).forEach { darkTheme ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
            waitUntilCatching("side tabs displayed at the expected width", timeout = 3.seconds) {
              composeRobot<AppShellRobot> { seeExpandedLayout(tabWidth = tabWidth) }
            }
            val dimensions = "${size.width.value.toInt()}x${size.height.value.toInt()}"
            saveScreenshot("sidebar-$dimensions-${if (darkTheme) "dark" else "light"}")
          }
        }
      }
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    val file = File("build/reports/screenshots/empty-shell-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
  }
}
