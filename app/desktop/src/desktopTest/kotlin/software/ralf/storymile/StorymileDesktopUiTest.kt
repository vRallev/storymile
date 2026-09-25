@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching
import software.ralf.storymile.library.LibraryRobot

class StorymileDesktopUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `phone shows empty library`() = uiTestRule.runPhoneRobotTest {
    waitUntilCatching("empty library displayed", timeout = 3.seconds) {
      composeRobot<LibraryRobot> { seeEmptyLibrary() }
    }
  }

  @Test
  fun `tablet shows empty library`() = uiTestRule.runTabletRobotTest {
    waitUntilCatching("empty library displayed", timeout = 3.seconds) {
      composeRobot<LibraryRobot> { seeEmptyLibrary() }
    }
  }
}
