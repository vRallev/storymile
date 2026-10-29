package software.ralf.storymile.episodedetails

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThanOrEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.testing.FakeThemeEnvironment

class EpisodeDetailsRendererTest {
  private val environment = FakeThemeEnvironment()

  @Test
  fun `full details scroll and back stays reachable across window sizes and large text`() {
    listOf(
        Triple(first = 320, second = 700, third = 1f),
        Triple(first = 412, second = 916, third = 1f),
        Triple(first = 800, second = 600, third = 1f),
        Triple(first = 1100, second = 360, third = 1f),
        Triple(first = 480, second = 1000, third = 2f),
      )
      .forEach { (width, height, fontScale) ->
        runDesktopComposeUiTest(width = width, height = height) {
          var backs = 0
          val invokedActions = mutableListOf<String>()
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
                          onPlayNow = { invokedActions += "play" },
                          onAddToQueue = { invokedActions += "add-to-queue" },
                          onSave = { invokedActions += "save" },
                          onDownload = { invokedActions += "download" },
                          onMore = { invokedActions += "more" },
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
          listOf("play", "add-to-queue", "save", "download", "more").forEach { action ->
            onNodeWithTag("episode-details-$action")
              .performScrollTo()
              .assertIsDisplayed()
              .assertIsEnabled()
              .performClick()
            runOnIdle {
              assertThat(invokedActions).containsExactly(action)
              invokedActions.clear()
            }
          }
          onNodeWithTag("episode-details-actions").performScrollTo()
          val actionsFile = File(file.parentFile, "episode-actions-$width-$height.png")
          ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", actionsFile)
          listOf("Play now", "Add to queue").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
              SemanticsActions.GetTextLayoutResult,
            ) {
              it(layouts)
            }
            val layout = layouts.single()
            assertThat(layout.lineCount, "$label stays on one line at $width and $fontScale")
              .isEqualTo(1)
            assertThat(layout.didOverflowHeight).isFalse()
            assertThat(layout.getLineEnd(0)).isEqualTo(label.length)
            assertThat(layout.getLineRight(0) - layout.getLineLeft(0))
              .isLessThanOrEqualTo(layout.size.width.toFloat())
          }
          onNodeWithTag("episode-details-title").assertTextEquals(details.episode.title)
          runOnIdle { assertThat(backs).isEqualTo(0) }
          onNodeWithTag("episode-details-description")
            .performScrollTo()
            .assertTextEquals(details.description)
          onNodeWithTag("episode-details-back").assertIsDisplayed().performClick()
          runOnIdle { assertThat(backs).isEqualTo(1) }
        }
      }
  }

  @Test
  fun `selecting another episode resets an embedded pane to the top`() =
    runDesktopComposeUiTest(width = 480, height = 600) {
      val first = details("<p>A long episode description.</p>".repeat(100))
      val model =
        mutableStateOf(
          EpisodeDetailsPresenterImpl.Model(
            details = first,
            onPlayNow = {},
            onAddToQueue = {},
            onSave = {},
            onDownload = {},
            onMore = {},
            onBack = {},
          ),
        )
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(1f),
          LocalEpisodeDetailsPane provides true,
        ) {
          StorymileTheme(environment = environment, darkTheme = false) {
            EpisodeDetailsRenderer().renderCompose(model.value, Modifier)
          }
        }
      }
      onNodeWithTag("episode-details-content").performSemanticsAction(SemanticsActions.ScrollBy) {
        it(0f, 1_500f)
      }
      onNodeWithTag("episode-details-artwork").assertIsNotDisplayed()
      onNodeWithTag("episode-details-title").assertIsNotDisplayed()

      runOnIdle {
        model.value =
          model.value.copy(
            details = first.copy(episode = first.episode.copy(id = "two", title = "Next episode")),
          )
      }

      onNodeWithTag("episode-details-artwork").assertIsDisplayed()
      onNodeWithTag("episode-details-title").assertIsDisplayed().assertTextEquals("Next episode")
      onNodeWithTag("episode-details-back").assertDoesNotExist()
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
                    onPlayNow = {},
                    onAddToQueue = {},
                    onSave = {},
                    onDownload = {},
                    onMore = {},
                    onBack = {},
                  ),
                modifier = Modifier,
              )
          }
        }
      }
      onNodeWithText("No description available.").performScrollTo().assertIsDisplayed()
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
