package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isGreaterThan
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.testing.FakeThemeEnvironment

class ShowContentRendererTest {
  private val themeEnvironment = FakeThemeEnvironment()

  @Test
  fun `info expands below the artwork and follow button and collapses across window sizes`() {
    listOf(
        Triple(412, 916, 1f),
        Triple(471, 600, 1f),
        Triple(472, 600, 1f),
        Triple(759, 600, 1f),
        Triple(760, 600, 1f),
        Triple(1100, 360, 1f),
        Triple(480, 1000, 2f),
      )
      .forEach { (width, height, fontScale) ->
        runDesktopComposeUiTest(width = width, height = height) {
          val state = mutableStateOf(model())
          state.value =
            state.value.copy(
              onToggleDescription = {
                state.value =
                  state.value.copy(descriptionExpanded = !state.value.descriptionExpanded)
              },
            )
          setContent {
            ThemedContent(fontScale) {
              ShowContentRenderer().renderCompose(state.value, Modifier.fillMaxSize())
            }
          }
          onNodeWithTag("show-description").assertDoesNotExist()
          onNodeWithContentDescription("Show podcast description").assertIsOff().performClick()
          onNodeWithTag("show-info").assertIsOn()
          val description = onNodeWithTag("show-description").getUnclippedBoundsInRoot()
          assertThat(description.top)
            .isGreaterThan(onNodeWithTag("show-artwork").getUnclippedBoundsInRoot().bottom)
          assertThat(description.top)
            .isGreaterThan(onNodeWithTag("show-follow").getUnclippedBoundsInRoot().bottom)
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("show-description"))
          onNodeWithText(
              "Stories about science and the natural world.\nNew conversations every week.",
            )
            .assertIsDisplayed()
          if (width == 472) {
            val file = File("build/reports/screenshots/show-description-expanded.png")
            file.parentFile.mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
          }
          onNodeWithTag("show-detail").performScrollToIndex(0)
          onNodeWithContentDescription("Hide podcast description").performClick()
          onNodeWithTag("show-description").assertDoesNotExist()
          onNodeWithTag("show-info").assertIsOff()
        }
      }
  }

  @Test
  fun `expanded description updates when missing content arrives`() =
    runDesktopComposeUiTest(width = 600, height = 900) {
      val loaded = model().copy(descriptionExpanded = true)
      val state = mutableStateOf(loaded.copy(podcast = loaded.podcast.copy(description = null)))
      setContent {
        ThemedContent { ShowContentRenderer().renderCompose(state.value, Modifier.fillMaxSize()) }
      }
      onNodeWithText("No description available.").assertIsDisplayed()
      runOnIdle { state.value = loaded }
      onNodeWithText(requireNotNull(loaded.podcast.description)).assertIsDisplayed()
      onNodeWithText("No description available.").assertDoesNotExist()
    }

  @Composable
  private fun ThemedContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
      StorymileTheme(environment = themeEnvironment, darkTheme = true) {
        Surface(color = AppTheme.colorScheme.background, content = content)
      }
    }
  }

  private fun model(): ShowContentPresenter.Model {
    val podcast =
      DiscoveredPodcast(
        id = "show",
        title = "The Quiet Current",
        author = "A publisher",
        feedUrl = "feed",
        artworkUrl = null,
        catalogUrl = null,
        description = "Stories about science and the natural world.\nNew conversations every week.",
      )
    return ShowContentPresenter.Model(
      podcast = podcast,
      podcasts = listOf(podcast),
      source = ShowPresenter.Source.Browse,
      episodes =
        EpisodesPresenter.Model(
          episodes = emptyList(),
          loading = false,
          failed = false,
          onRetry = {},
        ),
      descriptionExpanded = false,
      onToggleDescription = {},
      onPodcastSelected = {},
      onBack = {},
    )
  }
}
