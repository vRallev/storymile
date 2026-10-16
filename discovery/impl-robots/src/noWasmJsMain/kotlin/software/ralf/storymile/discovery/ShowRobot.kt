package software.ralf.storymile.discovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.zacsweers.metro.AppScope
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.ComposeRobot
import software.ralf.storymile.util.runOnUiThread

/** Drives show previews without changing playback or the library. */
@ContributesRobot(AppScope::class)
class ShowRobot : ComposeRobot() {
  /** Checks that the show detail has been rendered. */
  fun seeShow() {
    compose.waitUntilCatching("Show preview displayed") {
      compose.onNodeWithTag("show-screen").assertIsDisplayed()
      compose.onNodeWithTag("show-detail").assertIsDisplayed()
    }
  }

  /** Checks preview actions and the episode row before playback is connected. */
  fun pressPreviewActions() {
    runOnUiThread(compose) {
      compose.onNodeWithTag("show-detail").performScrollToNode(hasTestTag("show-follow"))
      compose.onNodeWithTag("show-follow").performClick()
    }
    compose.onNodeWithTag("show-follow").assertTextContains("Follow")
    compose.waitUntilCatching("Episodes loaded") {
      runOnUiThread(compose) {
        compose.onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-play-0"))
      }
      compose.onNodeWithTag("episode-play-0").assertIsDisplayed().assertIsNotEnabled()
    }
    compose.onNodeWithTag("show-screen").assertIsDisplayed()
    compose.onNodeWithTag("playback").assertDoesNotExist()
  }

  /** Returns to the list that opened the show. */
  fun back() {
    compose.onNodeWithTag("show-back").assertIsDisplayed()
    runOnUiThread(compose) { compose.onNodeWithTag("show-back").performClick() }
    compose.waitUntilCatching("Catalog displayed") {
      compose.onNodeWithTag("discovery-screen").assertIsDisplayed()
    }
  }
}
