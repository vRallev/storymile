package software.ralf.storymile.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test
import software.ralf.storymile.library.LibraryPresenterImpl.Model
import software.ralf.storymile.library.LibraryPresenterImpl.Tab
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeEnvironment

class LibraryRendererTest {
  private val themeEnvironment =
    object : ThemeEnvironment {
      @Composable
      override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
        content()
      }
    }

  @Test
  fun `phone short wide and enlarged text layouts fit without scrolling`() {
    listOf(Triple(320, 568, 1f), Triple(900, 360, 1f), Triple(480, 700, 1.5f)).forEach {
      (width, height, fontScale) ->
      runDesktopComposeUiTest(width = width, height = height) {
        val renderer = LibraryRenderer()
        var tab by mutableStateOf(Tab.QUEUE)
        setContent {
          ThemedContent(fontScale) { renderer.renderCompose(model(tab), Modifier.fillMaxSize()) }
        }
        Tab.entries.forEach { next ->
          runOnIdle { tab = next }
          assertContentFits(next)
          assertReadableLabels()
        }
        val illustration =
          pageNode(tab = Tab.SAVED, tag = "library-illustration").getUnclippedBoundsInRoot()
        val message =
          pageNode(tab = Tab.SAVED, tag = "library-empty-message").getUnclippedBoundsInRoot()
        if (width >= 600 && height < 480) {
          assertThat(illustration.right).isLessThanOrEqualTo(message.left)
        } else {
          assertThat(illustration.bottom).isLessThanOrEqualTo(message.top)
        }
      }
    }
  }

  @Test
  fun `illustration shrinks when the window gets shorter while controls remain visible`() =
    runDesktopComposeUiTest(width = 480, height = 840) {
      val renderer = LibraryRenderer()
      var height by mutableStateOf(840.dp)
      setContent {
        ThemedContent {
          renderer.renderCompose(model(), Modifier.size(width = 480.dp, height = height))
        }
      }
      val original =
        pageNode(tab = Tab.QUEUE, tag = "library-illustration").getUnclippedBoundsInRoot()
      runOnIdle { height = 400.dp }
      assertContentFits()
      assertReadableLabels()
      val resized =
        pageNode(tab = Tab.QUEUE, tag = "library-illustration").getUnclippedBoundsInRoot()
      assertThat(resized.bottom - resized.top).isLessThan(original.bottom - original.top)
    }

  @Test
  fun `tab clicks and swipes stay synchronized and retain selection when returning`() =
    runDesktopComposeUiTest(width = 480, height = 840) {
      val renderer = LibraryRenderer()
      val presenter = LibraryPresenterImpl()
      var visible by mutableStateOf(true)
      setContent {
        val stateHolder = rememberSaveableStateHolder()
        ThemedContent {
          if (visible) {
            stateHolder.SaveableStateProvider("library") {
              renderer.renderCompose(presenter.present(Unit), Modifier.fillMaxSize())
            }
          }
        }
      }

      assertSelectedTab(tab = Tab.QUEUE, title = "Your queue is empty")
      onNodeWithTag("library-tab-saved").performClick()
      assertSelectedTab(tab = Tab.SAVED, title = "No saved episodes")
      onNodeWithTag("library-pager").performTouchInput { swipeRight() }
      assertSelectedTab(tab = Tab.SHOWS, title = "No followed shows")
      onNodeWithTag("library-pager").performTouchInput { swipeRight() }
      assertSelectedTab(tab = Tab.QUEUE, title = "Your queue is empty")
      onNodeWithTag("library-pager").performTouchInput { swipeLeft() }
      assertSelectedTab(tab = Tab.SHOWS, title = "No followed shows")
      onNodeWithTag("library-pager").performTouchInput { swipeLeft() }
      assertSelectedTab(tab = Tab.SAVED, title = "No saved episodes")

      runOnIdle { visible = false }
      onNodeWithTag("library-empty").assertDoesNotExist()
      runOnIdle { visible = true }
      assertSelectedTab(tab = Tab.SAVED, title = "No saved episodes")
      onNodeWithTag("library-tab-queue").performClick()
      assertSelectedTab(tab = Tab.QUEUE, title = "Your queue is empty")
    }

  private fun ComposeUiTest.assertSelectedTab(tab: Tab, title: String) {
    onNodeWithTag("library-tab-${tab.name.lowercase()}").assertIsSelected()
    onNodeWithText(title).assertIsDisplayed()
    assertContentFits(tab)
  }

  private fun ComposeUiTest.pageNode(tab: Tab, tag: String) =
    onNode(
      matcher =
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("library-page-${tab.name.lowercase()}")),
      useUnmergedTree = true,
    )

  private fun ComposeUiTest.assertContentFits(tab: Tab = Tab.QUEUE) {
    onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        useUnmergedTree = true,
      )
      .assertCountEquals(0)
    val root = onNodeWithTag("library-empty").getUnclippedBoundsInRoot()
    listOf("tab-queue", "tab-shows", "tab-saved", "illustration", "empty-message", "discover")
      .forEach { tag ->
        val node =
          if (tag.startsWith("tab-")) {
            onNodeWithTag("library-$tag", useUnmergedTree = true)
          } else {
            pageNode(tab = tab, tag = "library-$tag")
          }
        val bounds = node.assertIsDisplayed().getUnclippedBoundsInRoot()
        assertThat(bounds.left, tag).isGreaterThanOrEqualTo(root.left)
        assertThat(bounds.top, tag).isGreaterThanOrEqualTo(root.top)
        assertThat(bounds.right, tag).isLessThanOrEqualTo(root.right)
        assertThat(bounds.bottom, tag).isLessThanOrEqualTo(root.bottom)
      }
  }

  private fun ComposeUiTest.assertReadableLabels() {
    val labels =
      onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
        useUnmergedTree = true,
      )
    labels.fetchSemanticsNodes().indices.forEach { index ->
      val layouts = mutableListOf<TextLayoutResult>()
      labels[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
      layouts.forEach { layout ->
        val text = layout.layoutInput.text.text
        // Compose can reuse a wider aligned paragraph. Compare the line widths.
        for (line in 0 until layout.lineCount) {
          assertThat(layout.isLineEllipsized(line), text).isFalse()
          assertThat(layout.getLineRight(line) - layout.getLineLeft(line), text)
            .isLessThanOrEqualTo(layout.size.width.toFloat())
          assertThat(layout.getLineBottom(line), text)
            .isLessThanOrEqualTo(layout.size.height.toFloat())
        }
        assertThat(layout.getLineEnd(layout.lineCount - 1), text).isEqualTo(text.length)
      }
    }
  }

  private fun model(tab: Tab = Tab.QUEUE) =
    Model(selectedTab = tab, onSelectTab = {}, onDiscoverPodcasts = {})

  @Composable
  private fun ThemedContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
      StorymileTheme(environment = themeEnvironment, darkTheme = false) {
        Box(Modifier.fillMaxSize().background(AppTheme.colorScheme.background)) { content() }
      }
    }
  }
}
