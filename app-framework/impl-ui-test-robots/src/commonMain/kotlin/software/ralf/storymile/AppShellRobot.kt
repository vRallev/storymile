package software.ralf.storymile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
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
      compose.onNodeWithTag(it).assertIsDisplayed().onChildren().assertCountEquals(0)
    }
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

  /** Checks that the rail extends behind playback, which spans both columns. */
  fun seeExpandedLayout() {
    seeEmptyLayers()
    val content = compose.onNodeWithTag("library").getUnclippedBoundsInRoot()
    val playback = compose.onNodeWithTag("playback").getUnclippedBoundsInRoot()
    val tabs = compose.onNodeWithTag("tabs").getUnclippedBoundsInRoot()
    assertThat(tabs.right).isEqualTo(content.left)
    assertThat(tabs.bottom).isEqualTo(playback.bottom)
    assertThat(content.bottom).isEqualTo(playback.top)
    assertThat(tabs.left).isEqualTo(playback.left)
    assertThat(content.right).isEqualTo(playback.right)
  }
}
