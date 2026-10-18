@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
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

  @Test
  fun `search stays aligned while a back gesture reverses cancels and completes`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.desktop).forEach { size ->
      listOf(false, true).forEach { darkTheme ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
          onNodeWithTag("home-search").assertIsEnabled().assertHasClickAction().performClick()
          waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag("discovery-search")).fetchSemanticsNodes().isNotEmpty()
          }
          onNodeWithTag("discovery-search").performTextReplacement("Nature")
          val root = onRoot().fetchSemanticsNode().boundsInRoot
          val distance = minOf(300f, root.width / 2)
          mouse {
            moveTo(Offset(2f, root.height / 2))
            press()
            moveTo(Offset(2f + distance * 0.7f, root.height / 2))
          }
          assertSearchIconsAligned("discovery-search", "home-search", "back preview")
          mouse { moveTo(Offset(2f + distance * 0.2f, root.height / 2)) }
          assertSearchIconsAligned("discovery-search", "home-search", "back reversal")
          mouse { release() }
          onNodeWithTag("home-search").assertDoesNotExist()
          onNodeWithTag("discovery-search").assertTextContains("Nature")

          mouse {
            moveTo(Offset(2f, root.height / 2))
            press()
            moveTo(Offset(2f + distance * 0.7f, root.height / 2))
          }
          assertSearchIconsAligned("discovery-search", "home-search", "back completion")
          mouse { release() }
          onNodeWithTag("discovery-search").assertDoesNotExist()
          onNodeWithTag("home-search").assertIsEnabled().assertHasClickAction()
        }
      }
    }
  }

  private fun ComposeUiTest.mouse(block: MouseInjectionScope.() -> Unit) {
    runOnUiThread { onRoot().performMouseInput(block) }
    waitForIdle()
  }

  private fun ComposeUiTest.assertSearchIconsAligned(
    sourceSearch: String,
    targetSearch: String,
    name: String,
  ) {
    val sourceIcon = searchIconBounds(sourceSearch)
    val targetIcon = searchIconBounds(targetSearch)
    // The shared overlay moves both fields. Compare their content within those bounds.
    val sourceInset = sourceIcon.center - bounds(sourceSearch).topLeft
    val targetInset = targetIcon.center - bounds(targetSearch).topLeft
    assertThat(
        (sourceInset - targetInset).getDistance(),
        "Search icons stay aligned during $sourceSearch to $targetSearch in $name",
      )
      .isLessThan(0.5f)
    assertThat(abs(sourceIcon.width - targetIcon.width), "Search icon widths match in $name")
      .isLessThan(0.5f)
    assertThat(abs(sourceIcon.height - targetIcon.height), "Search icon heights match in $name")
      .isLessThan(0.5f)
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
          assertSearchIconsAligned(sourceSearch, targetSearch, name)
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

  private fun ComposeUiTest.searchIconBounds(searchTag: String): Rect = runOnUiThread {
    val coordinates =
      onNode(
          hasTestTag("search-icon") and hasAnyAncestor(hasTestTag(searchTag)),
          useUnmergedTree = true,
        )
        .fetchSemanticsNode()
        .layoutInfo
        .coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private data class Frame(val search: Rect, val title: Offset, val profile: Rect)

  private data class Scenario(val size: DpSize, val fontScale: Float = 1f) {
    val label: String = "${size.width.value.toInt()}x${size.height.value.toInt()}-$fontScale"
  }
}
