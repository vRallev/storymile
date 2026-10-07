package software.ralf.storymile

import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching
import software.ralf.storymile.tabs.TabsPresenter

class StorymileAndroidUiTest {
  @get:Rule val uiTestRule = AndroidUiTestRule()

  @Test
  fun shows_app_navigation_and_content() = uiTestRule.runRobotTest {
    waitUntilCatching("app navigation and content displayed", timeout = 5.seconds) {
      composeRobot<AppShellRobot> { seeAppLayers() }
    }
  }

  @Test
  fun playback_swipes_preserve_the_selected_tab() = uiTestRule.runRobotTest {
    composeRobot<AppShellRobot> {
      selectTab(TabsPresenter.Tab.LIBRARY)
      dragPlaybackUp()
    }
    waitUntilCatching("playback expanded", timeout = 5.seconds) {
      composeRobot<AppShellRobot> { seePlaybackScreen(phone = true) }
    }
    composeRobot<AppShellRobot> { dragPlaybackDown() }
    waitUntilCatching("selected tab restored", timeout = 5.seconds) {
      composeRobot<AppShellRobot> { seeSelectedTab(TabsPresenter.Tab.LIBRARY) }
    }
  }
}
