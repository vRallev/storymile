@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching

class PlaybackVisibilityUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `empty playlists hide playback and playlist changes update the visible app`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size) {
        composeRobot<AppShellRobot> { seeAppLayers(playbackVisible = false) }
        composeRobot<PlaylistRobot> { addPlayablePlaylist() }
        waitUntilCatching("playback available", timeout = 3.seconds) {
          composeRobot<AppShellRobot> { seeAppLayers() }
        }
        composeRobot<AppShellRobot> { openPlayback() }
        composeRobot<PlaylistRobot> { clearPlaylists() }
        waitUntilCatching("playback removed", timeout = 3.seconds) {
          onNodeWithTag("playback").assertDoesNotExist()
          onNodeWithTag("playback-screen").assertDoesNotExist()
          composeRobot<AppShellRobot> { seeAppLayers(playbackVisible = false) }
        }
      }
    }
  }
}
