@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isStrictlyBetween
import assertk.assertions.isTrue
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import software.ralf.app.platform.presenter.compose.text.PresenterTextFieldState
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeEnvironment

class DiscoveryRendererTest {
  private val themeEnvironment =
    object : ThemeEnvironment {
      @Composable
      override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
        content()
      }
    }

  @Test
  fun `search collapses topics and their spacing while clearing restores them`() {
    listOf(412 to 916, 759 to 480, 760 to 480, 900 to 360).forEach { (width, height) ->
      runDesktopComposeUiTest(width = width, height = height) {
        val renderer = DiscoveryRenderer()
        val model =
          DiscoveryPresenterImpl.Model(
            episodes = EpisodesPresenter.Model(emptyList(), false, false, {}),
            topic = null,
            query = PresenterTextFieldState(),
            state = CatalogState.Loading,
            focusSearch = false,
            selectedFilter = null,
            onFilterSelected = {},
            onTopicSelected = {},
            onPodcastSelected = {},
            onRetry = {},
            onBack = {},
          )
        setContent {
          CompositionLocalProvider(LocalDensity provides Density(1f)) {
            StorymileTheme(environment = themeEnvironment, darkTheme = false) {
              renderer.renderCompose(model, Modifier)
            }
          }
        }

        val search = onNodeWithTag("discovery-search")
        val topics = onNodeWithTag("discovery-topics")
        val content = onNodeWithTag("discovery-results-title")
        search.performClick()
        topics.assertIsDisplayed()
        search.performTextReplacement(" ")
        topics.assertIsDisplayed()
        val searchBounds = search.getUnclippedBoundsInRoot()
        val topicsBounds = topics.getUnclippedBoundsInRoot()
        val expandedContentTop = content.getUnclippedBoundsInRoot().top
        val collapsedContentTop = expandedContentTop - (topicsBounds.bottom - topicsBounds.top)

        mainClock.autoAdvance = false
        try {
          search.performTextReplacement("Nature")
          mainClock.advanceTimeByFrame()
          mainClock.advanceTimeByFrame()
          mainClock.advanceTimeBy(80)
          assertThat(content.getUnclippedBoundsInRoot().top)
            .isStrictlyBetween(collapsedContentTop, expandedContentTop)
          topics.assertExists()

          mainClock.autoAdvance = true
          waitForIdle()
          topics.assertDoesNotExist()
          onNodeWithTag("discovery-topic-NATURE").assertDoesNotExist()
          assertThat(content.getUnclippedBoundsInRoot().top).isEqualTo(collapsedContentTop)
          assertThat(search.getUnclippedBoundsInRoot()).isEqualTo(searchBounds)

          mainClock.autoAdvance = false
          onNodeWithTag("discovery-clear").performClick()
          mainClock.advanceTimeByFrame()
          mainClock.advanceTimeByFrame()
          mainClock.advanceTimeBy(80)
          assertThat(content.getUnclippedBoundsInRoot().top)
            .isStrictlyBetween(collapsedContentTop, expandedContentTop)

          mainClock.autoAdvance = true
          waitForIdle()
          topics.assertIsDisplayed()
          onNodeWithTag("discovery-topic-NATURE").assertIsDisplayed()
          assertThat(
              content.getUnclippedBoundsInRoot().top - search.getUnclippedBoundsInRoot().bottom,
            )
            .isEqualTo(expandedContentTop - searchBounds.bottom)
        } finally {
          mainClock.autoAdvance = true
        }
      }
    }
  }

  @Test
  fun `heading survives loading and catalog content fades in both directions`() {
    listOf(412, 1000).forEach { width ->
      runDesktopComposeUiTest(width = width, height = 1000) {
        val query = PresenterTextFieldState()
        val featured = CatalogState.Ready(listOf(podcast("featured"), podcast("second")))
        val results = CatalogState.Ready(listOf(podcast("result")))
        val state = mutableStateOf<CatalogState>(featured)
        val renderer = DiscoveryRenderer()
        setContent { ThemedContent { renderer.renderCompose(model(query, state.value), Modifier) } }
        onNodeWithTag("discovery-search").performClick()
        val fieldBounds = onNodeWithTag("discovery-search").getUnclippedBoundsInRoot()
        val featuredContrast = artworkContrast("featured")
        saveFrame("$width-browse")

        mainClock.autoAdvance = false
        try {
          runOnIdle {
            query.replaceText("g")
            state.value = CatalogState.Loading
          }
          advanceTransition()
          onNodeWithText("Featured podcasts").assertExists()
          onNodeWithText("Search results").assertExists()
          assertHeadingDrawn()
          assertThat(artworkContrast("featured")).isStrictlyBetween(0f, featuredContrast)
          saveFrame("$width-search-transition")
          settle()
          onNodeWithTag("discovery-podcast-featured").assertDoesNotExist()
          onNodeWithText("Search results").assertIsDisplayed()
          assertHeadingDrawn()
          saveFrame("$width-search-loading")

          mainClock.autoAdvance = false
          runOnIdle { state.value = results }
          advanceTransition()
          val appearingResult = artworkContrast("result")
          settle()
          val resultContrast = artworkContrast("result")
          assertThat(appearingResult).isStrictlyBetween(0f, resultContrast)

          mainClock.autoAdvance = false
          runOnIdle {
            query.replaceText("")
            state.value = CatalogState.Loading
          }
          advanceTransition()
          onNodeWithText("Search results").assertExists()
          onNodeWithText("Featured podcasts").assertExists()
          assertHeadingDrawn()
          assertThat(artworkContrast("result")).isStrictlyBetween(0f, resultContrast)
          saveFrame("$width-browse-transition")
          settle()
          onNodeWithText("Featured podcasts").assertIsDisplayed()
          onNodeWithTag("discovery-podcast-result").assertDoesNotExist()
          assertHeadingDrawn()

          mainClock.autoAdvance = false
          runOnIdle { state.value = featured }
          advanceTransition()
          val appearingFeatured = artworkContrast("featured")
          settle()
          assertThat(appearingFeatured).isStrictlyBetween(0f, artworkContrast("featured"))
          onNodeWithTag("discovery-search").assertIsFocused()
          assertThat(onNodeWithTag("discovery-search").getUnclippedBoundsInRoot())
            .isEqualTo(fieldBounds)
          saveFrame("$width-restored")
        } finally {
          mainClock.autoAdvance = true
        }
      }
    }
  }

  @Test
  fun `placeholder and clear button fade without replacing the focused search field`() =
    runDesktopComposeUiTest(width = 412, height = 916) {
      val renderer = DiscoveryRenderer()
      val model = model(PresenterTextFieldState(), CatalogState.Loading)
      setContent { ThemedContent { renderer.renderCompose(model, Modifier) } }
      val search = onNodeWithTag("discovery-search")
      search.performClick()
      val fieldBounds = search.getUnclippedBoundsInRoot()
      val placeholderContrast = inkContrast("discovery-search-placeholder", inset = 40)
      mainClock.autoAdvance = false
      try {
        search.performTextReplacement("g")
        advanceTransition()
        assertThat(inkContrast("discovery-search-placeholder", inset = 40))
          .isStrictlyBetween(0f, placeholderContrast)
        val appearingClear = inkContrast("discovery-clear")
        settle()
        onNodeWithTag("discovery-search-placeholder").assertDoesNotExist()
        val clearContrast = inkContrast("discovery-clear")
        assertThat(appearingClear).isStrictlyBetween(0f, clearContrast)

        mainClock.autoAdvance = false
        search.performTextReplacement("")
        advanceTransition()
        assertThat(inkContrast("discovery-clear")).isStrictlyBetween(0f, clearContrast)
        assertThat(inkContrast("discovery-search-placeholder", inset = 40))
          .isStrictlyBetween(0f, placeholderContrast)
        settle()
        onNodeWithTag("discovery-clear").assertDoesNotExist()
        onNodeWithTag("discovery-search-placeholder").assertIsDisplayed()
        search.assertIsFocused()
        assertThat(search.getUnclippedBoundsInRoot()).isEqualTo(fieldBounds)
      } finally {
        mainClock.autoAdvance = true
      }
    }

  private fun ComposeUiTest.advanceTransition() {
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeBy(32)
  }

  private fun ComposeUiTest.settle() {
    mainClock.autoAdvance = true
    waitForIdle()
  }

  private fun ComposeUiTest.assertHeadingDrawn() {
    assertThat(inkContrast("discovery-results-title")).isGreaterThan(0.1f)
  }

  private fun ComposeUiTest.artworkContrast(id: String): Float {
    val bounds = onNodeWithTag("discovery-podcast-$id").getUnclippedBoundsInRoot()
    val pixels = onRoot().captureToImage().toPixelMap()
    return contrast(
      pixels[(bounds.left.value + 20).roundToInt(), (bounds.top.value + 20).roundToInt()],
      pixels[0, 0],
    )
  }

  private fun ComposeUiTest.inkContrast(tag: String, inset: Int = 0): Float {
    val bounds = onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    val pixels = onRoot().captureToImage().toPixelMap()
    var maximum = 0f
    for (y in bounds.top.value.roundToInt() until bounds.bottom.value.roundToInt()) {
      for (x in bounds.left.value.roundToInt() + inset until bounds.right.value.roundToInt()) {
        maximum = maxOf(maximum, contrast(pixels[x, y], pixels[0, 0]))
      }
    }
    return maximum
  }

  private fun contrast(color: Color, background: Color): Float =
    abs(color.red - background.red) +
      abs(color.green - background.green) +
      abs(color.blue - background.blue)

  private fun ComposeUiTest.saveFrame(name: String) {
    val file = File("build/reports/screenshots/discovery-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
  }

  private fun podcast(id: String) = DiscoveredPodcast(id, id, null, null, null, null)

  private fun model(query: PresenterTextFieldState, state: CatalogState) =
    DiscoveryPresenterImpl.Model(
      episodes = EpisodesPresenter.Model(emptyList(), false, false, {}),
      topic = null,
      query = query,
      state = state,
      focusSearch = false,
      selectedFilter = null,
      onFilterSelected = {},
      onTopicSelected = {},
      onPodcastSelected = {},
      onRetry = {},
      onBack = {},
    )

  @Composable
  private fun ThemedContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(1f)) {
      StorymileTheme(environment = themeEnvironment, darkTheme = false) {
        Box(Modifier.fillMaxSize().background(AppTheme.colorScheme.background)) { content() }
      }
    }
  }

  @Test
  fun `search controls return above results when scrolling up far from the top`() {
    listOf(Triple(412, 916, 1f), Triple(900, 360, 1f), Triple(800, 1280, 1f), Triple(480, 1000, 2f))
      .forEach { (width, height, fontScale) ->
        runDesktopComposeUiTest(width = width, height = height) {
          val model = model()
          setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
              StorymileTheme(environment = themeEnvironment, darkTheme = width == 800) {
                Surface(color = AppTheme.colorScheme.background) {
                  DiscoveryRenderer().renderCompose(model, Modifier.fillMaxSize())
                }
              }
            }
          }
          val header = onNodeWithTag("discovery-search-header")
          val initialHeader = header.getUnclippedBoundsInRoot()
          onNodeWithTag("discovery-back").assertIsDisplayed()
          onNodeWithTag("discovery-search").assertIsDisplayed()
          repeat(4) {
            onNodeWithTag("discovery-list").performTouchInput { swipeUp(durationMillis = 600) }
          }
          onNodeWithTag("discovery-back").assertIsNotDisplayed()
          onNodeWithTag("discovery-search").assertIsNotDisplayed()

          // Reverse only a short distance, while keeping the pointer down to inspect the
          // transition.
          onNodeWithTag("discovery-list").performTouchInput {
            down(Offset(centerX, height * 0.25f))
            moveBy(Offset(0f, 60f), delayMillis = 200)
          }
          val partialHeader = header.getUnclippedBoundsInRoot()
          assertThat(partialHeader.bottom).isGreaterThan(0.dp)
          assertThat(partialHeader.bottom).isLessThan(initialHeader.bottom)
          onNodeWithTag("discovery-list").performTouchInput {
            moveBy(Offset(0f, (initialHeader.bottom - initialHeader.top).value), delayMillis = 600)
            up()
          }
          onNodeWithTag("discovery-back").assertIsDisplayed()
          onNodeWithTag("discovery-search").assertIsDisplayed().assertTextContains("Podcast")
          assertScrolledBelowStart()
          val visibleHeader = header.getUnclippedBoundsInRoot()
          val results = onAllNodes(hasTestTag("discovery-list")).fetchSemanticsNodes().single()
          assertThat(results.boundsInRoot.top).isLessThan(visibleHeader.bottom.value)
          val rows =
            onAllNodes(
                SemanticsMatcher("Podcast row") {
                  it.config
                    .getOrElse(SemanticsProperties.TestTag) { "" }
                    .startsWith("discovery-podcast-")
                },
              )
              .fetchSemanticsNodes()
          assertThat(
              rows.any {
                it.boundsInRoot.top < visibleHeader.bottom.value && it.boundsInRoot.bottom > 0f
              },
            )
            .isTrue()
          saveScreenshot("$width-$height-$fontScale")

          onNodeWithTag("discovery-search").performTextReplacement("Updated query")
          onNodeWithTag("discovery-search").assertTextContains("Updated query")
          repeat(2) {
            onNodeWithTag("discovery-list").performTouchInput { swipeUp(durationMillis = 600) }
          }
          onNodeWithTag("discovery-search").assertIsNotDisplayed()
          onNodeWithTag("discovery-list").performScrollToIndex(0)
          onNodeWithTag("discovery-search").assertIsDisplayed().assertTextContains("Updated query")
        }
      }
  }

  private fun ComposeUiTest.assertScrolledBelowStart() {
    val range =
      onNodeWithTag("discovery-list")
        .fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange]
    assertThat(range.value()).isGreaterThan(0f)
    onNodeWithTag("discovery-topic-NATURE").assertDoesNotExist()
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    val file = File("build/reports/screenshots/discovery-scroll-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("discovery-screen").captureToImage().toAwtImage(), "png", file)
  }

  private fun model() =
    DiscoveryPresenterImpl.Model(
      episodes = EpisodesPresenter.Model(emptyList(), false, false, {}),
      topic = null,
      query = PresenterTextFieldState("Podcast"),
      state =
        CatalogState.Ready(
          List(60) { index ->
            DiscoveredPodcast("podcast-$index", "Podcast $index", "Author $index", null, null, null)
          },
        ),
      focusSearch = false,
      selectedFilter = null,
      onFilterSelected = {},
      onTopicSelected = {},
      onPodcastSelected = {},
      onRetry = {},
      onBack = {},
    )
}
