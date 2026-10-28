package software.ralf.storymile.discovery.presentation

import software.ralf.storymile.shows.ShowEpisodes
import software.ralf.storymile.shows.Episodes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
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
  fun `follow button animates both directions and shows retryable failures`() {
    listOf(false, true).forEach { darkTheme ->
      runDesktopComposeUiTest(width = 600, height = 900) {
        val state = mutableStateOf(model())
        state.value =
          state.value.copy(
            onToggleFollow = { state.value = state.value.copy(followed = !state.value.followed) },
          )
        setContent {
          StorymileTheme(environment = themeEnvironment, darkTheme = darkTheme) {
            Surface(color = AppTheme.colorScheme.background) {
              ShowContentRenderer().renderCompose(state.value, Modifier.fillMaxSize())
            }
          }
        }
        listOf(true, false).forEach { followed ->
          val original = followButtonColor()
          mainClock.autoAdvance = false
          onNodeWithTag("show-follow").assertIsEnabled().performClick()
          mainClock.advanceTimeByFrame()
          mainClock.advanceTimeBy(80)
          val intermediate = followButtonColor()
          mainClock.advanceTimeBy(1000)
          val settled = followButtonColor()
          assertThat(intermediate).isNotEqualTo(original)
          assertThat(intermediate).isNotEqualTo(settled)
          mainClock.autoAdvance = true
          onNodeWithTag("show-follow").assertTextContains(if (followed) "Following" else "Follow")
          if (followed) {
            val file =
              File(
                "build/reports/screenshots/show-following-${if (darkTheme) "dark" else "light"}.png",
              )
            file.parentFile.mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
          }
        }
        runOnIdle { state.value = state.value.copy(followEnabled = false) }
        onNodeWithTag("show-follow").assertIsNotEnabled()
        runOnIdle { state.value = state.value.copy(followEnabled = true, followFailed = true) }
        onNodeWithTag("show-follow").assertIsEnabled()
        onNodeWithTag("show-follow-error").assertIsDisplayed()
      }
    }
  }

  private fun ComposeUiTest.followButtonColor() =
    onNodeWithTag("show-follow").captureToImage().toPixelMap().let { it[12, it.height / 2] }

  @Test
  fun `follow controls retain Material sizing across display densities`() {
    listOf(Density(1f), Density(2f), Density(3f), Density(density = 2f, fontScale = 1.5f))
      .forEach { density ->
        listOf(false, true).forEach { followed ->
          runDesktopComposeUiTest(
            width = (600 * density.density).toInt(),
            height = (500 * density.density).toInt(),
          ) {
            val model = model().copy(followed = followed)
            val label = if (followed) "Following" else "Follow"
            setContent {
              CompositionLocalProvider(LocalDensity provides density) {
                StorymileTheme(environment = themeEnvironment, darkTheme = true) {
                  Surface(color = AppTheme.colorScheme.background) {
                    Column {
                      Box(Modifier.weight(1f)) {
                        ShowContentRenderer().renderCompose(model, Modifier.fillMaxSize())
                      }
                      // The original control uses Material's vector icon and inherited button text.
                      Button(
                        onClick = {},
                        modifier = Modifier.widthIn(min = 180.dp).testTag("follow-reference"),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                      ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(
                            imageVector = if (followed) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.testTag("follow-reference-icon"),
                          )
                          Spacer(Modifier.width(8.dp))
                          Text(label)
                        }
                      }
                    }
                  }
                }
              }
            }
            val reference = onNodeWithTag("follow-reference").getUnclippedBoundsInRoot()
            onNodeWithTag("show-follow")
              .assertWidthIsEqualTo(reference.right - reference.left)
              .assertHeightIsEqualTo(reference.bottom - reference.top)
            val referenceIcon =
              onNodeWithTag("follow-reference-icon", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            onNodeWithTag("show-follow-icon", useUnmergedTree = true)
              .assertWidthIsEqualTo(referenceIcon.right - referenceIcon.left)
              .assertHeightIsEqualTo(referenceIcon.bottom - referenceIcon.top)
            val layouts = mutableListOf<TextLayoutResult>()
            val labels = onAllNodesWithText(label, useUnmergedTree = true)
            repeat(2) { index ->
              labels[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
              }
            }
            assertThat(layouts[0].layoutInput.style.fontSize)
              .isEqualTo(layouts[1].layoutInput.style.fontSize)
            assertThat(layouts[0].size).isEqualTo(layouts[1].size)
          }
        }
      }
  }

  @Test
  fun `podcast title wrapping moves the header and episodes smoothly in both directions`() =
    runDesktopComposeUiTest(width = 600, height = 800) {
      val targetWidth = mutableStateOf(520.dp)
      val model =
        model().let { it.copy(podcast = it.podcast.copy(title = "The Joe Rogan Experience")) }
      setContent {
        ThemedContent {
          val paneWidth by
            animateDpAsState(targetValue = targetWidth.value, animationSpec = tween(450))
          Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalShowPane provides true) {
              ShowContentRenderer().renderCompose(model, Modifier.width(paneWidth))
            }
          }
        }
      }
      assertThat(titleLineCount()).isEqualTo(2)
      mainClock.autoAdvance = false
      try {
        listOf(320.dp to 3, 520.dp to 2).forEach { (width, lines) ->
          var previousFollow = onNodeWithTag("show-follow").getUnclippedBoundsInRoot().top.value
          var previousEpisodes = onNodeWithText("Episodes").getUnclippedBoundsInRoot().top.value
          runOnUiThread { targetWidth.value = width }
          repeat(60) {
            mainClock.advanceTimeByFrame()
            val follow = onNodeWithTag("show-follow").getUnclippedBoundsInRoot().top.value
            val episodes = onNodeWithText("Episodes").getUnclippedBoundsInRoot().top.value
            assertThat(kotlin.math.abs(follow - previousFollow)).isLessThan(8f)
            assertThat(kotlin.math.abs(episodes - previousEpisodes)).isLessThan(8f)
            previousFollow = follow
            previousEpisodes = episodes
          }
          assertThat(titleLineCount()).isEqualTo(lines)
        }
      } finally {
        mainClock.autoAdvance = true
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

  private fun ComposeUiTest.titleLineCount(): Int {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithTag("show-title").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
      it(layouts)
    }
    return layouts.single().lineCount
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
        ShowEpisodes(
          episodes = emptyList(),
          loading = false,
          failed = false,
          onRetry = {},
        ),
      descriptionExpanded = false,
      followed = false,
      followEnabled = true,
      followFailed = false,
      onToggleFollow = {},
      onToggleDescription = {},
      onPodcastSelected = {},
      onBack = {},
    )
  }
}
