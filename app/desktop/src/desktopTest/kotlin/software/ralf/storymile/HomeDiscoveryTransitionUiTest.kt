@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test

class HomeDiscoveryTransitionUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `title search and profile animate between home and discovery in both directions`() {
    listOf(
        Scenario(DpSize(412.dp, 916.dp)),
        Scenario(DpSize(599.dp, 480.dp)),
        Scenario(DpSize(600.dp, 480.dp)),
        Scenario(DpSize(900.dp, 360.dp)),
        Scenario(DpSize(1440.dp, 900.dp)),
        Scenario(DpSize(480.dp, 1000.dp), fontScale = 2f),
      )
      .forEach { scenario ->
        uiTestRule.runRobotTest(
          windowSize = scenario.size,
          darkTheme = false,
          fontScale = scenario.fontScale,
        ) {
          waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag("home-search")).fetchSemanticsNodes().isNotEmpty()
          }
          val homeTitle =
            if (
              onAllNodes(hasTestTag("tab-brand-name"), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
            ) {
              "tab-brand-name"
            } else {
              "tab-title"
            }
          transition(
            "home-search",
            "discovery-search",
            homeTitle,
            "title-bar-title",
            "home-profile-icon",
            "discovery-profile-icon",
            scenario.label,
          ) {
            onNodeWithTag("home-search").performClick()
          }
          onNodeWithTag("discovery-search").assertIsFocused()
          transition(
            "discovery-search",
            "home-search",
            "title-bar-title",
            homeTitle,
            "discovery-profile-icon",
            "home-profile-icon",
            scenario.label,
          ) {
            onNodeWithTag("discovery-back").performClick()
          }
          onNodeWithTag("discovery-screen").assertDoesNotExist()
        }
      }
  }

  private fun ComposeUiTest.transition(
    sourceSearch: String,
    targetSearch: String,
    sourceTitle: String,
    targetTitle: String,
    sourceProfile: String,
    targetProfile: String,
    name: String,
    navigate: ComposeUiTest.() -> Unit,
  ) {
    val startSearch = bounds(sourceSearch)
    val startTitle = titlePosition(sourceTitle)
    val startProfile = bounds(sourceProfile)
    mainClock.autoAdvance = false
    try {
      runOnUiThread { navigate() }
      waitUntil(timeoutMillis = 5_000) {
        mainClock.advanceTimeByFrame()
        runOnUiThread {
          onAllNodes(hasTestTag(targetSearch)).fetchSemanticsNodes().isNotEmpty()
        }
      }
      val frames =
        (1..2).map {
          mainClock.advanceTimeBy(48)
          if (targetSearch == "discovery-search") {
            onNodeWithTag(targetSearch).assertIsNotFocused()
          }
          Frame(bounds(targetSearch), titlePosition(targetTitle), bounds(targetProfile))
        }
      val screenshot =
        File("build/reports/screenshots/discovery-transition-$name-$targetSearch.png")
      screenshot.parentFile.mkdirs()
      ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", screenshot)
      mainClock.advanceTimeBy(2_000)
      val endSearch = bounds(targetSearch)
      val endTitle = titlePosition(targetTitle)
      val endProfile = bounds(targetProfile)
      frames.forEach { frame ->
        assertThat(
            abs(frame.search.top - startSearch.top),
            "Search moves between endpoints in $name",
          )
          .isLessThan(abs(endSearch.top - startSearch.top))
        assertThat(
            abs(frame.search.top - endSearch.top) > 1f,
            "Search has an intermediate frame in $name",
          )
          .isTrue()
        assertThat(
            (frame.profile.center - startProfile.center).getDistance(),
            "Profile moves between endpoints in $name",
          )
          .isLessThan((endProfile.center - startProfile.center).getDistance())
        assertThat(
            abs(frame.profile.width - startProfile.width),
            "Profile keeps the shared icon size in $name",
          )
          .isLessThan(0.5f)
        assertThat(
            (frame.profile.center - endProfile.center).getDistance() > 0.5f,
            "Profile has an intermediate position in $name",
          )
          .isTrue()
        assertThat(
            (frame.title - startTitle).getDistance(),
            "Title moves between endpoints in $name",
          )
          .isLessThan((endTitle - startTitle).getDistance())
        assertThat(
            (frame.title - endTitle).getDistance() > 0.5f,
            "Title has an intermediate frame in $name",
          )
          .isTrue()
      }
    } finally {
      mainClock.autoAdvance = true
    }
    waitForIdle()
  }

  private fun ComposeUiTest.bounds(tag: String): Rect = runOnUiThread {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    // Shared content draws in an overlay outside its original parent clipping bounds.
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun ComposeUiTest.titlePosition(tag: String): Offset {
    val bounds = bounds(tag)
    return Offset(bounds.left, bounds.center.y)
  }

  private data class Frame(val search: Rect, val title: Offset, val profile: Rect)

  private data class Scenario(val size: DpSize, val fontScale: Float = 1f) {
    val label: String = "${size.width.value.toInt()}x${size.height.value.toInt()}-$fontScale"
  }
}
