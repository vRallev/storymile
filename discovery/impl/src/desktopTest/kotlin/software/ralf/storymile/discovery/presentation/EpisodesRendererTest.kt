@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.rss.PodcastEnclosure
import software.ralf.storymile.rss.PodcastEpisode
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeEnvironment

class EpisodesRendererTest {
  private val themeEnvironment =
    object : ThemeEnvironment {
      @Composable
      override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
        content()
      }
    }

  @Test
  fun `episode section renders loading empty and retryable partial failures`() =
    runDesktopComposeUiTest(width = 412, height = 916) {
      var retries = 0
      val state = mutableStateOf(EpisodesPresenter.Model(emptyList(), true, false, { retries++ }))
      setContent {
        ThemedContent {
          Column { Episodes("Episodes to try", state.value, Modifier) }
        }
      }
      onNodeWithTag("episodes-loading").assertIsDisplayed()
      runOnIdle { state.value = state.value.copy(loading = false) }
      onNodeWithTag("episodes-empty").assertIsDisplayed()
      runOnIdle { state.value = state.value.copy(episodes = listOf(episode(0)), failed = true) }
      onNodeWithTag("episodes-error").assertIsDisplayed()
      onNodeWithText("Episode 0: Stories from the natural world").assertIsDisplayed()
      onNodeWithText("42:00").assertIsDisplayed()
      onNodeWithTag("episode-play-0").assertIsNotEnabled()
      onNodeWithTag("episodes-retry").performClick()
      runOnIdle { assertThat(retries).isEqualTo(1) }
    }

  @Test
  fun `show episodes scroll in narrow wide and large text layouts`() {
    listOf(
        Triple(412, 916, 1f),
        Triple(759, 600, 1f),
        Triple(760, 600, 1f),
        Triple(1100, 360, 1f),
        Triple(480, 1000, 2f),
      )
      .forEach { (width, height, fontScale) ->
        runDesktopComposeUiTest(width = width, height = height) {
          val podcast =
            DiscoveredPodcast("show", "The Quiet Current", "A publisher", "feed", null, null)
          val model =
            ShowPresenter.Model(
              podcast = podcast,
              podcasts = listOf(podcast),
              source = ShowPresenter.Source.Browse,
              episodes = EpisodesPresenter.Model(List(100) { episode(it) }, false, false, {}),
              descriptionExpanded = false,
              onToggleDescription = {},
              onPodcastSelected = {},
              onBack = {},
            )
          setContent {
            ThemedContent(fontScale) {
              ShowRenderer().renderCompose(model, Modifier.fillMaxSize())
            }
          }
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-0"))
          onNodeWithTag("episode-play-0").assertIsDisplayed().assertIsNotEnabled()
          onNodeWithText("Sample episode 1").assertDoesNotExist()
          if (width == 412) {
            val file = File("build/reports/screenshots/feed-episodes-phone.png")
            file.parentFile.mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
          }
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-99"))
          onNodeWithTag("episode-99").assertIsDisplayed()
        }
      }
  }

  @Composable
  private fun ThemedContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
      StorymileTheme(environment = themeEnvironment, darkTheme = false) {
        Surface(color = AppTheme.colorScheme.background, content = content)
      }
    }
  }

  private fun episode(index: Int) =
    EpisodesPresenter.Episode(
      "feed",
      "The Quiet Current",
      null,
      PodcastEpisode(
        "$index",
        "Episode $index: Stories from the natural world",
        null,
        null,
        null,
        null,
        42.minutes,
        PodcastEnclosure("https://audio.test/$index.mp3", "audio/mpeg", null),
      ),
    )
}
