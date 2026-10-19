package software.ralf.storymile

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.playlist.PlaylistRobot

class PlaybackVisibilityUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `empty playlists hide playback and playlist changes update the visible app`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size) {
        composeRobot<AppShellRobot> { seeAppLayers(playbackVisible = false) }
        composeRobot<PlaylistRobot> { addPlayablePlaylist() }
        waitUntil("playback available", timeoutMillis = 3_000) {
          onAllNodes(hasTestTag("playback")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRobot<AppShellRobot> { seeAppLayers() }
        composeRobot<AppShellRobot> { openPlayback() }
        composeRobot<PlaylistRobot> { clearPlaylists() }
        waitUntil("playback removed", timeoutMillis = 3_000) {
          onAllNodes(hasTestTag("playback") or hasTestTag("playback-screen"))
            .fetchSemanticsNodes()
            .isEmpty()
        }
        onNodeWithTag("playback").assertDoesNotExist()
        onNodeWithTag("playback-screen").assertDoesNotExist()
        composeRobot<AppShellRobot> { seeAppLayers(playbackVisible = false) }
      }
    }
  }
}
