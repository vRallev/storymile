@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot

class PlayerLayoutUiTest {
  private val uiTestRule = DesktopUiTestRule(withEpisodes = true)

  @Test
  fun `artwork shrinks and grows when the player height changes`() {
    val size = mutableStateOf(DpSize(377.dp, 900.dp))
    uiTestRule.runRobotTest(windowSize = size.value, contentSize = size) {
      composeRobot<AppShellRobot> { openPlayback() }
      assertPlayerFits()
      val initialArtwork = artworkHeight()

      runOnIdle { size.value = DpSize(377.dp, 640.dp) }
      assertPlayerFits()
      assertThat(artworkHeight()).isLessThan(initialArtwork)

      runOnIdle { size.value = DpSize(377.dp, 900.dp) }
      assertPlayerFits()
      assertThat(artworkHeight()).isCloseTo(initialArtwork, 1f)
    }
  }

  private fun ComposeUiTest.assertPlayerFits() {
    onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
          hasAnyAncestor(hasTestTag("playback-screen")),
      )
      .fetchSemanticsNodes()
      .forEach { node ->
        assertThat(node.config[SemanticsProperties.VerticalScrollAxisRange].maxValue())
          .isEqualTo(0f)
      }
    listOf(
        "artwork-expanded",
        "title-expanded",
        "subtitle-expanded",
        "bookmark",
        "download",
        "seek",
        "play-pause",
        "speed",
        "sleep",
        "queue",
        "chapters",
        "current-chapter",
      )
      .forEach { part ->
        val node =
          onNodeWithTag("playback-$part", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertThat(node.boundsInRoot.height, "$part must not be clipped")
          .isCloseTo(node.size.height.toFloat(), 1f)
      }
    assertThat(artworkHeight()).isGreaterThan(0f)
  }

  private fun ComposeUiTest.artworkHeight(): Float {
    return onNodeWithTag("playback-artwork-expanded", useUnmergedTree = true)
      .fetchSemanticsNode()
      .boundsInRoot
      .height
  }
}
