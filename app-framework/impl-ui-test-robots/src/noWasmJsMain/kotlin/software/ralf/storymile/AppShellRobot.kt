package software.ralf.storymile

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.zacsweers.metro.AppScope
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.ComposeRobot
import software.ralf.app.platform.robot.waitUntilCatching
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.util.runOnUiThread

/** Checks the visible app containers through the production renderer graph. */
@ContributesRobot(AppScope::class)
class AppShellRobot : ComposeRobot() {
  /** Checks the initial destination, navigation items, and playback container. */
  fun seeAppLayers(playbackVisible: Boolean = true) {
    listOf("tab-content", "tabs").forEach {
      compose.onNodeWithTag(it).assertIsDisplayed()
    }
    if (playbackVisible) {
      compose.onNodeWithTag("playback").assertIsDisplayed()
      compose
        .onNodeWithTag("playback-artwork-collapsed", useUnmergedTree = true)
        .assertIsDisplayed()
    } else {
      compose.onNodeWithTag("playback").assertDoesNotExist()
      compose.onNodeWithTag("playback-screen").assertDoesNotExist()
    }
    compose
      .onAllNodes(
        hasAnyAncestor(hasTestTag("tabs")) and
          SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
      )
      .assertCountEquals(3)
    seeSelectedTab(TabsPresenter.Tab.HOME)
  }

  /** Chooses a destination through the visible navigation item. */
  fun selectTab(tab: TabsPresenter.Tab) {
    runOnUiThread(compose) { compose.onNodeWithTag("tab-${tab.name.lowercase()}").performClick() }
    seeSelectedTab(tab)
  }

  /** Checks that exactly one navigation item is selected and its content is visible. */
  fun seeSelectedTab(tab: TabsPresenter.Tab) {
    waitForSelection(tab) {
      TabsPresenter.Tab.entries.forEach {
        val item = compose.onNodeWithTag("tab-${it.name.lowercase()}").assertIsDisplayed()
        if (it == tab) {
          item.assertIsSelected()
        } else {
          item.assertIsNotSelected()
        }
      }
      if (tab == TabsPresenter.Tab.HOME) {
        compose.onNodeWithTag("home-screen").assertIsDisplayed()
      } else if (tab == TabsPresenter.Tab.DOWNLOADS) {
        compose.onNodeWithTag("downloads-screen").assertIsDisplayed()
        compose.onNodeWithText("Listen anywhere").assertIsDisplayed()
      } else {
        compose.onNodeWithTag("library-empty").assertIsDisplayed()
        compose.onNodeWithTag("library-filter-episodes").assertIsDisplayed()
        compose.onNodeWithTag("library-filter-shows").assertIsDisplayed()
        compose.onNodeWithTag("library-discover").assertIsDisplayed()
      }
    }
  }

  @OptIn(ExperimentalTestApi::class)
  private fun waitForSelection(tab: TabsPresenter.Tab, assertion: () -> Unit) {
    val description = "Tab $tab selected"
    val uiTest = compose as? ComposeUiTest
    if (uiTest == null) {
      waitUntilCatching(description) { assertion() }
    } else {
      uiTest.waitUntil(description, timeoutMillis = 10_000) {
        try {
          assertion()
          true
        } catch (_: AssertionError) {
          false
        }
      }
    }
  }

  /** Checks that side navigation includes the app's logo and title. */
  fun seeBranding(visible: Boolean) {
    val logo = compose.onNodeWithTag("storymile-logo")
    val title = compose.onNode(hasText("Storymile") and hasAnyAncestor(hasTestTag("tabs")))
    if (visible) {
      logo.assertIsDisplayed()
      title.assertIsDisplayed()
    } else {
      logo.assertDoesNotExist()
      title.assertDoesNotExist()
    }
  }

  /** Drags the persistent playback bar to the expanded screen. */
  fun dragPlaybackUp() {
    val height = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
    runOnUiThread(compose) {
      compose.onNodeWithTag("playback").performTouchInput {
        swipe(
          start = Offset(40f, 32f),
          end = Offset(40f, 32f - height * 0.8f),
          durationMillis = 500,
        )
      }
    }
  }

  /** Opens playback through its click action, also available to keyboard users. */
  fun openPlayback() {
    runOnUiThread(compose) {
      compose
        .onNodeWithTag("playback-artwork-collapsed", useUnmergedTree = true)
        .performTouchInput {
          click(center)
        }
    }
  }

  /** Returns to the app through the player header button. */
  fun collapsePlayback() {
    compose.onNodeWithTag("playback-collapse").assertContentDescriptionEquals("Collapse player")
    runOnUiThread(compose) { compose.onNodeWithTag("playback-collapse").performClick() }
  }

  /** Drags the expanded screen back to its persistent bar. */
  fun dragPlaybackDown() {
    runOnUiThread(compose) {
      compose.onNodeWithTag("playback-screen").performTouchInput {
        swipeDown(startY = 32f, endY = height - 32f, durationMillis = 500)
      }
    }
  }

  /** Checks that expanded playback occupies the window and covers phone tabs. */
  fun seePlaybackScreen(phone: Boolean) {
    val playback = compose.onNodeWithTag("playback-screen").assertIsDisplayed()
    compose.onNodeWithText("Now playing").assertIsDisplayed()
    compose.onNodeWithTag("playback-artwork-expanded").assertIsDisplayed()
    assertThat(playback.getUnclippedBoundsInRoot())
      .isEqualTo(compose.onNodeWithTag("app-shell").getUnclippedBoundsInRoot())
    if (phone) compose.onNodeWithTag("tabs").assertIsNotDisplayed()
  }

  /** Checks that playback starts below content and extends behind bottom tabs. */
  fun seeCompactLayout() {
    seeAppLayers()
    val content = compose.onNodeWithTag("tab-content").getUnclippedBoundsInRoot()
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
  fun seeExpandedLayout(tabWidth: Dp = 112.dp) {
    seeAppLayers()
    val content = compose.onNodeWithTag("tab-content").getUnclippedBoundsInRoot()
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
