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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
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
  fun `episode artwork moves and grows with reversible inline details`() =
    runDesktopComposeUiTest(width = 412, height = 916) {
      setContent {
        ThemedContent {
          Episodes(
            title = "Episodes to try",
            model =
              EpisodesPresenter.Model(
                episodes = listOf(episode(0), episode(1)),
                loading = false,
                failed = false,
                onRetry = {},
              ),
            modifier = Modifier,
          )
        }
      }
      val row = onNodeWithTag("episode-0")
      val artwork = onNodeWithTag("episode-artwork-0", useUnmergedTree = true)
      val compactRow = row.getUnclippedBoundsInRoot()
      val compactArtwork = artwork.getUnclippedBoundsInRoot()
      val nextRow = onNodeWithTag("episode-1").getUnclippedBoundsInRoot()
      onNodeWithText("A journey through nature & science.").assertDoesNotExist()
      mainClock.autoAdvance = false
      row.performClick()
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(100)
      val movingImage = onRoot().captureToImage()
      val movingPixels = movingImage.toPixelMap()
      // Shared-element semantics use target bounds; inspect the drawn artwork during motion.
      val artworkPixels =
        (0 until movingPixels.width).filter { x ->
          val color = movingPixels[x, compactRow.top.value.toInt() + 20]
          color.blue - color.red > 0.025f
        }
      assertThat(artworkPixels.size).isGreaterThan(56)
      assertThat(artworkPixels.size).isLessThan(184)
      assertThat(artworkPixels.first()).isGreaterThan(compactArtwork.left.value.toInt())
      val movingFile = File("build/reports/screenshots/expanding-episode-phone.png")
      movingFile.parentFile.mkdirs()
      ImageIO.write(movingImage.toAwtImage(), "png", movingFile)
      mainClock.autoAdvance = true
      onNodeWithText("A journey through nature & science.", useUnmergedTree = true)
        .assertIsDisplayed()
      onNodeWithText("By A storyteller", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithText("Published Sun, 18 Oct 2026 08:00:00 GMT", useUnmergedTree = true)
        .assertIsDisplayed()
      val expandedArtwork = artwork.getUnclippedBoundsInRoot()
      val expandedRow = row.getUnclippedBoundsInRoot()
      assertThat((expandedArtwork.right - expandedArtwork.left).value).isEqualTo(184f)
      assertThat(
          abs(
            (expandedArtwork.left + expandedArtwork.right - expandedRow.left - expandedRow.right)
              .value,
          ),
        )
        .isLessThan(2f)
      assertThat(expandedRow.bottom - expandedRow.top)
        .isGreaterThan(compactRow.bottom - compactRow.top)
      assertThat(onNodeWithTag("episode-1").getUnclippedBoundsInRoot().top)
        .isGreaterThan(nextRow.top)
      val file = File("build/reports/screenshots/expanded-episode-phone.png")
      file.parentFile.mkdirs()
      ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
      row.performClick()
      onNodeWithTag("episode-details-0", useUnmergedTree = true).assertDoesNotExist()
      assertThat(artwork.getUnclippedBoundsInRoot()).isEqualTo(compactArtwork)
      assertThat(row.getUnclippedBoundsInRoot()).isEqualTo(compactRow)

      mainClock.autoAdvance = false
      row.performClick()
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeBy(100)
      row.performClick()
      mainClock.autoAdvance = true
      onNodeWithTag("episode-details-0", useUnmergedTree = true).assertDoesNotExist()
      assertThat(row.getUnclippedBoundsInRoot()).isEqualTo(compactRow)
    }

  @Test
  fun `expansion follows episode identity when the feed changes order`() =
    runDesktopComposeUiTest(width = 412, height = 916) {
      val episodes = mutableStateOf(listOf(episode(0), episode(1)))
      setContent {
        ThemedContent {
          Episodes(
            title = null,
            model =
              EpisodesPresenter.Model(
                episodes = episodes.value,
                loading = false,
                failed = false,
                onRetry = {},
              ),
            modifier = Modifier,
          )
        }
      }
      onNodeWithTag("episode-0").performClick()
      onNodeWithTag("episode-details-0", useUnmergedTree = true).assertIsDisplayed()
      runOnIdle { episodes.value = episodes.value.reversed() }
      onNodeWithTag("episode-details-0", useUnmergedTree = true).assertDoesNotExist()
      onNodeWithTag("episode-details-1", useUnmergedTree = true).assertIsDisplayed()
    }

  @Test
  fun `episodes without metadata still expand and collapse`() =
    runDesktopComposeUiTest(width = 412, height = 916) {
      val item =
        episode(0).let {
          it.copy(episode = it.episode.copy(description = null, author = null, publishedAt = null))
        }
      setContent { ThemedContent { EpisodeRow(item, 0) } }
      onNodeWithTag("episode-0").performClick()
      onNodeWithText("No description available.", useUnmergedTree = true).assertIsDisplayed()
      onNodeWithTag("episode-0").performClick()
      onNodeWithText("No description available.", useUnmergedTree = true).assertDoesNotExist()
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
              podcast,
              listOf(podcast),
              ShowPresenter.Source.Browse,
              EpisodesPresenter.Model(List(100) { episode(it) }, false, false, {}),
              {},
              {},
            )
          setContent {
            ThemedContent(fontScale) {
              ShowRenderer().renderCompose(model, Modifier.fillMaxSize())
            }
          }
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-0"))
          onNodeWithTag("episode-play-0").assertIsDisplayed().assertIsNotEnabled()
          onNodeWithText("Sample episode 1").assertDoesNotExist()
          onNodeWithTag("episode-0").performClick()
          onNodeWithTag("show-detail", useUnmergedTree = true)
            .performScrollToNode(hasTestTag("episode-details-0"))
          val artwork =
            onNodeWithTag("episode-artwork-0", useUnmergedTree = true).getUnclippedBoundsInRoot()
          val row = onNodeWithTag("episode-0").getUnclippedBoundsInRoot()
          assertThat((artwork.right - artwork.left).value).isEqualTo(184f)
          assertThat(abs((artwork.left + artwork.right - row.left - row.right).value))
            .isLessThan(2f)
          onNodeWithTag("episode-play-0").assertIsNotEnabled()
          if (width == 412) {
            val file = File("build/reports/screenshots/feed-episodes-phone.png")
            file.parentFile.mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
          }
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-99"))
          onNodeWithTag("episode-99").assertIsDisplayed()
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-0"))
          onNodeWithTag("episode-details-0", useUnmergedTree = true).assertExists()
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
        "<p>A journey through <b>nature &amp; science</b>.</p>",
        "A storyteller",
        null,
        "Sun, 18 Oct 2026 08:00:00 GMT",
        42.minutes,
        PodcastEnclosure("https://audio.test/$index.mp3", "audio/mpeg", null),
      ),
    )
}
