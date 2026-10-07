package software.ralf.storymile

import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching

class StorymileAndroidUiTest {
  @get:Rule val uiTestRule = AndroidUiTestRule()

  @Test
  fun shows_app_navigation_and_content() = uiTestRule.runRobotTest {
    waitUntilCatching("app navigation and content displayed", timeout = 5.seconds) {
      composeRobot<AppShellRobot> { seeAppLayers() }
    }
  }
}
