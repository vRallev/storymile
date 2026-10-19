package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.rss.PodcastEnclosure
import software.ralf.storymile.rss.PodcastEpisode
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeEnvironment

class ShowRendererTest {
  private val themeEnvironment =
    object : ThemeEnvironment {
      @Composable
      override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
        content()
      }
    }

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
              ShowRenderer().renderCompose(state.value, Modifier.fillMaxSize())
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
        ThemedContent { ShowRenderer().renderCompose(state.value, Modifier.fillMaxSize()) }
      }
      onNodeWithText("No description available.").assertIsDisplayed()
      runOnIdle { state.value = loaded }
      onNodeWithText(requireNotNull(loaded.podcast.description)).assertIsDisplayed()
      onNodeWithText("No description available.").assertDoesNotExist()
    }

  @Test
  fun `podcast title returns during upward scrolling far from the start`() {
    listOf(
        Triple(412, 916, 1f),
        Triple(599, 480, 1f),
        Triple(600, 480, 1f),
        Triple(759, 600, 1f),
        Triple(760, 600, 1f),
        Triple(1100, 360, 1f),
        Triple(480, 1000, 2f),
      )
      .forEach { (width, height, fontScale) ->
        runDesktopComposeUiTest(width = width, height = height) {
          val model = model(episodeCount = 100)
          setContent {
            ThemedContent(fontScale) {
              ShowRenderer().renderCompose(model, Modifier.fillMaxSize())
            }
          }
          val list = onNodeWithTag("show-detail")
          val header = onNodeWithTag("show-sticky-title")
          header.assertIsNotDisplayed()
          repeat(4) { list.performTouchInput { swipeUp(durationMillis = 600) } }
          header.assertIsNotDisplayed()
          onNodeWithTag("show-title").assertDoesNotExist()
          val scrollRange =
            list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
          val beforeReverse = scrollRange.value()
          val viewport = list.getUnclippedBoundsInRoot()
          list.performTouchInput {
            down(Offset(centerX, height * 0.25f))
            moveBy(Offset(0f, 30f), delayMillis = 200)
          }
          val partial = header.getUnclippedBoundsInRoot()
          assertThat(partial.bottom).isGreaterThan(viewport.top)
          assertThat(partial.top).isLessThan(viewport.top)
          assertThat(scrollRange.value()).isLessThan(beforeReverse)
          list.performTouchInput {
            moveBy(Offset(0f, (partial.bottom - partial.top).value), delayMillis = 600)
            up()
          }
          header.assertIsDisplayed()
          onNode(
              hasText(model.podcast.title) and hasAnyAncestor(hasTestTag("show-sticky-title")),
              useUnmergedTree = true,
            )
            .assertIsDisplayed()
          val visible = header.getUnclippedBoundsInRoot()
          assertThat(visible.top).isEqualTo(viewport.top)
          assertThat(visible.left).isEqualTo(viewport.left)
          assertThat(visible.right).isEqualTo(viewport.right)
          onNodeWithTag("episode-0").assertDoesNotExist()
          onNodeWithTag("show-back").assertIsDisplayed()
          val file =
            File("build/reports/screenshots/show-sticky-title-$width-$height-$fontScale.png")
          file.parentFile.mkdirs()
          ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)

          repeat(2) { list.performTouchInput { swipeUp(durationMillis = 600) } }
          header.assertIsNotDisplayed()
          list.performScrollToIndex(0)
          onNodeWithTag("show-title").assertIsDisplayed()
          header.assertIsNotDisplayed()
        }
      }
  }

  @Test
  fun `title returns while a long expanded description is still visible`() =
    runDesktopComposeUiTest(width = 600, height = 900) {
      val initial = model(episodeCount = 30)
      val model =
        initial.copy(
          podcast =
            initial.podcast.copy(
              description = "Stories about science and the natural world.\n".repeat(100),
            ),
          descriptionExpanded = true,
        )
      setContent {
        ThemedContent { ShowRenderer().renderCompose(model, Modifier.fillMaxSize()) }
      }
      val list = onNodeWithTag("show-detail")
      repeat(2) { list.performTouchInput { swipeUp(durationMillis = 600) } }
      onNodeWithTag("show-title").assertIsNotDisplayed()
      onNodeWithTag("show-sticky-title").assertIsNotDisplayed()
      onNodeWithTag("show-description").assertIsDisplayed()
      list.performTouchInput {
        down(Offset(centerX, height * 0.25f))
        moveBy(Offset(0f, 200f), delayMillis = 600)
        up()
      }
      onNodeWithTag("show-sticky-title").assertIsDisplayed()
      onNodeWithTag("show-title").assertIsNotDisplayed()
      onNodeWithTag("show-description").assertIsDisplayed()
      list.performScrollToIndex(0)
      onNodeWithTag("show-title").assertIsDisplayed()
      onNodeWithTag("show-sticky-title").assertIsNotDisplayed()
    }

  @Test
  fun `selecting a podcast resets its scroll and returning title`() =
    runDesktopComposeUiTest(width = 1100, height = 600) {
      val initial = model(episodeCount = 100)
      val next = initial.podcast.copy(id = "next", title = "Another podcast")
      val state = mutableStateOf(initial)
      state.value =
        initial.copy(
          podcasts = listOf(initial.podcast, next),
          onPodcastSelected = { podcast -> state.value = state.value.copy(podcast = podcast) },
        )
      setContent {
        ThemedContent { ShowRenderer().renderCompose(state.value, Modifier.fillMaxSize()) }
      }
      val list = onNodeWithTag("show-detail")
      list.performScrollToIndex(30)
      list.performTouchInput {
        down(Offset(centerX, height * 0.25f))
        moveBy(Offset(0f, 200f), delayMillis = 600)
        up()
      }
      onNodeWithTag("show-sticky-title").assertIsDisplayed()
      onNodeWithTag("discovery-podcast-next").performClick()
      onNodeWithTag("show-title").assertIsDisplayed().assertTextEquals(next.title)
      onNodeWithTag("show-sticky-title").assertIsNotDisplayed()
      list.performScrollToIndex(30)
      list.performTouchInput {
        down(Offset(centerX, height * 0.25f))
        moveBy(Offset(0f, 200f), delayMillis = 600)
        up()
      }
      onNode(
          hasText(next.title) and hasAnyAncestor(hasTestTag("show-sticky-title")),
          useUnmergedTree = true,
        )
        .assertIsDisplayed()
    }

  @Composable
  private fun ThemedContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
      StorymileTheme(environment = themeEnvironment, darkTheme = true) {
        Surface(color = AppTheme.colorScheme.background, content = content)
      }
    }
  }

  private fun model(episodeCount: Int = 0): ShowPresenter.Model {
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
    return ShowPresenter.Model(
      podcast = podcast,
      podcasts = listOf(podcast),
      source = ShowPresenter.Source.Browse,
      episodes =
        EpisodesPresenter.Model(
          episodes =
            List(episodeCount) { index ->
              EpisodesPresenter.Episode(
                details =
                  EpisodeDetails(
                    feedUrl = "feed",
                    podcastTitle = podcast.title,
                    artworkUrl = null,
                    episode =
                      PodcastEpisode(
                        id = "$index",
                        title = "Episode $index",
                        description = "A conversation about science and the natural world.",
                        author = podcast.author,
                        artworkUrl = null,
                        publishedAt = null,
                        duration = null,
                        enclosure =
                          PodcastEnclosure(
                            url = "https://audio.test/$index.mp3",
                            type = "audio/mpeg",
                            length = null,
                          ),
                      ),
                  ),
                onClick = {},
              )
            },
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
