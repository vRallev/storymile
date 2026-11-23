package software.ralf.storymile.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import software.ralf.app.platform.renderer.renderCompose
import software.ralf.storymile.playback.PlaybackPresenterImpl.Chapter
import software.ralf.storymile.playback.PlaybackPresenterImpl.PlayerModel
import software.ralf.storymile.player.testing.PreviewTestPlayer
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.testing.FakeThemeEnvironment

class PlaybackControlsRendererTest {
  @Test
  fun `optional controls display their state after reopening playback`() {
    renderPlayback(width = 390, height = 900) { expanded ->
      onNodeWithTag("playback-bookmark").performScrollTo().performClick()
      onNodeWithTag("playback-download").performClick()
      onNodeWithTag("playback-sleep").performScrollTo().performClick()
      onNodeWithTag("playback-option-sleep-30").performClick()

      runOnIdle { expanded.value = false }
      onNodeWithTag("playback-bar-play-pause").assertIsDisplayed()
      runOnIdle { expanded.value = true }

      onNodeWithTag("playback-bookmark").assertIsOn()
      onNodeWithTag("playback-download").assertIsOn()
      onNodeWithTag("playback-sleep").assertTextContains("30 min")
    }
  }

  @Test
  fun `short windows can scroll to the last chapter and select it`() {
    for (width in listOf(360, 900)) {
      renderPlayback(width = width, height = 480) {
        onNodeWithTag("playback-current-chapter").performScrollTo().performClick()
        onNodeWithTag("playback-option-chapter-7").assertDoesNotExist()
        onNodeWithTag("playback-options").performScrollToIndex(7)
        onNodeWithTag("playback-option-chapter-7").performClick()
        onNode(
            hasText("Chapter 8 of 8") and hasAnyAncestor(hasTestTag("playback-current-chapter")),
            useUnmergedTree = true,
          )
          .assertIsDisplayed()
      }
    }
  }

  private fun renderPlayback(
    width: Int,
    height: Int,
    test: ComposeUiTest.(MutableState<Boolean>) -> Unit,
  ) {
    runDesktopComposeUiTest(width = width, height = height) {
      val expanded = mutableStateOf(true)
      val presenter = PlaybackPresenterImpl(PreviewTestPlayer().apply { showPreview() })
      val screenSize = ScreenSize.from(width = width.dp, height = height.dp)
      setContent {
        val player = withSimulatedControls(checkNotNull(presenter.present(Unit).expanded).player)
        CompositionLocalProvider(LocalScreenSize provides screenSize) {
          StorymileTheme(environment = remember { FakeThemeEnvironment() }) {
            if (expanded.value) {
              PlaybackScreenRenderer()
                .renderCompose(PlaybackPresenterImpl.ScreenModel(player), Modifier)
            } else {
              PlaybackBarRenderer().renderCompose(PlaybackPresenterImpl.BarModel(player), Modifier)
            }
          }
        }
      }
      test(expanded)
    }
  }

  @Composable
  private fun withSimulatedControls(player: PlayerModel): PlayerModel {
    var bookmarked by remember(player.showTitle, player.title) { mutableStateOf(false) }
    var downloaded by remember(player.showTitle, player.title) { mutableStateOf(false) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    val chapters =
      remember(player.durationSeconds) {
        List(8) { index ->
          Chapter(title = "Chapter ${index + 1}", startSeconds = player.durationSeconds * index / 8)
        }
      }
    return player.copy(
      isBookmarked = bookmarked,
      isDownloaded = downloaded,
      sleepMinutes = sleepMinutes,
      chapters = chapters,
      activeChapterIndex = chapters.indexOfLast { it.startSeconds <= player.positionSeconds },
      onBookmark = { bookmarked = !bookmarked },
      onDownload = { downloaded = !downloaded },
      onSleep = { sleepMinutes = it },
      onChapter = { player.onSeek(chapters[it].startSeconds) },
    )
  }
}
