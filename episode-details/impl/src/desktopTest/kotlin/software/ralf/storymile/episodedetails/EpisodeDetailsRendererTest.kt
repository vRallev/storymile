@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.episodedetails

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import software.ralf.storymile.rss.PodcastEnclosure
import software.ralf.storymile.rss.PodcastEpisode
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeEnvironment

class EpisodeDetailsRendererTest {
  private val environment =
    object : ThemeEnvironment {
      @Composable
      override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) = content()
    }

  @Test
  fun `full details scroll and back stays reachable across window sizes and large text`() {
    listOf(
        Triple(first = 412, second = 916, third = 1f),
        Triple(first = 800, second = 600, third = 1f),
        Triple(first = 1100, second = 360, third = 1f),
        Triple(first = 480, second = 1000, third = 2f),
      )
      .forEach { (width, height, fontScale) ->
        runDesktopComposeUiTest(width = width, height = height) {
          var backs = 0
          val details =
            details("<p>A journey through nature &amp; science.</p>".repeat(30) + "<p>The end.</p>")
          setContent {
            CompositionLocalProvider(
              LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
              StorymileTheme(environment = environment, darkTheme = width == 800) {
                Surface(color = AppTheme.colorScheme.background) {
                  EpisodeDetailsRenderer()
                    .renderCompose(
                      model =
                        EpisodeDetailsPresenterImpl.Model(
                          details = details,
                          onBack = { backs++ },
                        ),
                      modifier = Modifier,
                    )
                }
              }
            }
          }
          onNodeWithTag("episode-details-artwork").assertIsDisplayed()
          val file = File("build/reports/screenshots/episode-details-$width-$height.png")
          file.parentFile.mkdirs()
          ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
          onNodeWithTag("episode-details-description")
            .performScrollTo()
            .assertTextEquals(details.description)
          onNodeWithTag("episode-details-back").assertIsDisplayed().performClick()
          runOnIdle { assertThat(backs).isEqualTo(1) }
        }
      }
  }

  @Test
  fun `missing optional metadata shows a description fallback`() =
    runDesktopComposeUiTest(width = 412, height = 916) {
      val details =
        details(null).let {
          it.copy(episode = it.episode.copy(author = null, publishedAt = null, duration = null))
        }
      setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          StorymileTheme(environment = environment, darkTheme = false) {
            EpisodeDetailsRenderer()
              .renderCompose(
                model =
                  EpisodeDetailsPresenterImpl.Model(
                    details = details,
                    onBack = {},
                  ),
                modifier = Modifier,
              )
          }
        }
      }
      onNodeWithText("No description available.").assertIsDisplayed()
      onNodeWithTag("episode-details-duration").assertDoesNotExist()
    }

  private fun details(description: String?) =
    EpisodeDetails(
      feedUrl = "feed",
      podcastTitle = "The Quiet Current",
      artworkUrl = null,
      episode =
        PodcastEpisode(
          id = "one",
          title = "Stories from the natural world",
          description = description,
          author = "A storyteller",
          artworkUrl = null,
          publishedAt = "Sun, 18 Oct 2026 08:00:00 GMT",
          duration = 42.minutes,
          enclosure =
            PodcastEnclosure(
              url = "https://audio.test/one.mp3",
              type = "audio/mpeg",
              length = null,
            ),
        ),
    )
}
