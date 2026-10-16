@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import java.io.File
import javax.imageio.ImageIO
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
