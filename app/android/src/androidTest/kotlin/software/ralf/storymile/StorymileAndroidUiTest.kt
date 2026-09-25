package software.ralf.storymile

import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching
import software.ralf.storymile.library.LibraryRobot

class StorymileAndroidUiTest {
  @get:Rule val uiTestRule = AndroidUiTestRule()

  @Test
  fun shows_empty_library() = uiTestRule.runRobotTest {
    waitUntilCatching("empty library displayed", timeout = 5.seconds) {
      composeRobot<LibraryRobot> { seeEmptyLibrary() }
    }
  }
}
