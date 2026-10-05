package software.ralf.storymile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.zacsweers.metro.AppScope
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.ComposeRobot

/** Checks the visible app containers through the production renderer graph. */
@ContributesRobot(AppScope::class)
class AppShellRobot : ComposeRobot() {
  /** Checks that all three containers are visible and have no content. */
  fun seeEmptyLayers() {
    listOf("library", "tabs", "playback").forEach {
      compose.onNodeWithTag(it).assertIsDisplayed()
    }
    listOf("library", "playback").forEach {
      compose.onNodeWithTag(it).onChildren().assertCountEquals(0)
    }
    compose
      .onAllNodes(
        hasAnyAncestor(hasTestTag("tabs")) and
          SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
      )
      .assertCountEquals(0)
  }

  /** Drags the persistent playback bar to the expanded screen. */
  fun dragPlaybackUp() {
    val height = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
    compose.onNodeWithTag("playback").performTouchInput {
      swipeUp(startY = 32f, endY = 32f - height * 0.8f, durationMillis = 500)
    }
  }

  /** Opens playback through its click action, also available to keyboard users. */
  fun openPlayback() {
    compose.onNodeWithTag("playback").performTouchInput { click(Offset(center.x, 32f)) }
  }

  /** Drags the expanded screen back to its persistent bar. */
  fun dragPlaybackDown() {
    compose.onNodeWithTag("playback-screen").performTouchInput {
      swipeDown(startY = 32f, endY = height - 32f, durationMillis = 500)
    }
  }

  /** Checks that expanded playback occupies the window and covers phone tabs. */
  fun seePlaybackScreen(phone: Boolean) {
    val playback = compose.onNodeWithTag("playback-screen").assertIsDisplayed()
    assertThat(playback.getUnclippedBoundsInRoot())
      .isEqualTo(compose.onRoot().getUnclippedBoundsInRoot())
    if (phone) compose.onNodeWithTag("tabs").assertIsNotDisplayed()
  }

  /** Checks that playback starts below content and extends behind bottom tabs. */
  fun seeCompactLayout() {
    seeEmptyLayers()
    val content = compose.onNodeWithTag("library").getUnclippedBoundsInRoot()
    val playback = compose.onNodeWithTag("playback").getUnclippedBoundsInRoot()
    val tabs = compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot()
    assertThat(content.bottom).isEqualTo(playback.top)
    assertThat(playback.bottom).isEqualTo(tabs.bottom)
    assertThat(content.left).isEqualTo(playback.left)
    assertThat(content.right).isEqualTo(playback.right)
    assertThat(playback.left).isEqualTo(tabs.left)
    assertThat(playback.right).isEqualTo(tabs.right)
  }

  /** Checks the tabs width and that the tabs extend behind playback, which spans both columns. */
  fun seeExpandedLayout(tabWidth: Dp = 96.dp) {
    seeEmptyLayers()
    val content = compose.onNodeWithTag("library").getUnclippedBoundsInRoot()
    val playback = compose.onNodeWithTag("playback").getUnclippedBoundsInRoot()
    val tabs = compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot()
    assertThat(tabs.right - tabs.left).isEqualTo(tabWidth)
    assertThat(tabs.right).isEqualTo(content.left)
    assertThat(tabs.bottom).isEqualTo(playback.bottom)
    assertThat(content.bottom).isEqualTo(playback.top)
    assertThat(tabs.left).isEqualTo(playback.left)
    assertThat(content.right).isEqualTo(playback.right)
  }
}
