package software.ralf.storymile

import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching

class StorymileAndroidUiTest {
  @get:Rule val uiTestRule = AndroidUiTestRule()

  @Test
  fun shows_empty_app_containers() = uiTestRule.runRobotTest {
    waitUntilCatching("empty app containers displayed", timeout = 5.seconds) {
      composeRobot<AppShellRobot> { seeEmptyLayers() }
    }
  }
}
