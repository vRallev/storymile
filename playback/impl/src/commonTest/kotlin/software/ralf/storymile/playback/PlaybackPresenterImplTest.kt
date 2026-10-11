package software.ralf.storymile.playback

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test

class PlaybackPresenterImplTest {
  @Test
  fun `collapsed and expanded controls share playback state`() = runTest {
    val presenter = PlaybackPresenterImpl()

    presenter.testPlayer(this) {
      var model = awaitItem()
      assertThat(model.collapsed.player).isSameInstanceAs(model.expanded.player)
      assertThat(model.expanded.player.activeChapterIndex).isEqualTo(2)

      model.collapsed.player.onPlayPause()
      model = awaitItem()
      assertThat(model.expanded.player.isPlaying).isFalse()

      model.expanded.player.onBookmark()
      model = awaitItem()
      assertThat(model.collapsed.player.isBookmarked).isTrue()

      model.expanded.player.onDownload()
      model = awaitItem()
      assertThat(model.collapsed.player.isDownloaded).isTrue()

      model.expanded.player.onSpeed(1.5f)
      model = awaitItem()
      model.expanded.player.onSleep(30)
      model = awaitItem()
      model.collapsed.player.onPlayPause()
      model = awaitItem()
      assertThat(model.expanded.player.isPlaying).isTrue()
      assertThat(model.expanded.player.isBookmarked).isTrue()
      assertThat(model.expanded.player.isDownloaded).isTrue()
      assertThat(model.expanded.player.speed).isEqualTo(1.5f)
      assertThat(model.expanded.player.sleepMinutes).isEqualTo(30)
      assertThat(model.collapsed.player).isSameInstanceAs(model.expanded.player)
    }
  }

  @Test
  fun `seeking and skips stay within the current episode`() = runTest {
    val presenter = PlaybackPresenterImpl()

    presenter.testPlayer(this) {
      var player = awaitItem().expanded.player
      player.onSkipBack()
      player = awaitItem().expanded.player
      assertThat(player.positionSeconds).isEqualTo(739)

      player.onSkipForward()
      player = awaitItem().expanded.player
      assertThat(player.positionSeconds).isEqualTo(769)

      player.onSeek(5)
      player = awaitItem().expanded.player
      player.onSkipBack()
      player = awaitItem().expanded.player
      assertThat(player.positionSeconds).isEqualTo(0)
      assertThat(player.activeChapterIndex).isEqualTo(0)

      player.onSeek(player.durationSeconds - 5)
      player = awaitItem().expanded.player
      player.onSkipForward()
      player = awaitItem().expanded.player
      assertThat(player.positionSeconds).isEqualTo(player.durationSeconds)
      assertThat(player.activeChapterIndex).isEqualTo(player.chapters.lastIndex)

      player.onSeek(-100)
      player = awaitItem().expanded.player
      assertThat(player.positionSeconds).isEqualTo(0)

      player.onSeek(Int.MAX_VALUE)
      player = awaitItem().expanded.player
      assertThat(player.positionSeconds).isEqualTo(player.durationSeconds)
    }
  }

  @Test
  fun `chapter selection and seeking keep the active chapter in sync`() = runTest {
    val presenter = PlaybackPresenterImpl()

    presenter.testPlayer(this) {
      var player = awaitItem().expanded.player
      player.onChapter(4)
      player = awaitItem().expanded.player
      assertThat(player.positionSeconds).isEqualTo(player.chapters[4].startSeconds)
      assertThat(player.activeChapterIndex).isEqualTo(4)

      player.onSeek(player.chapters[4].startSeconds - 1)
      player = awaitItem().expanded.player
      assertThat(player.activeChapterIndex).isEqualTo(3)

      player.onChapter(-1)
      player.onChapter(player.chapters.size)
      expectNoEvents()
    }
  }

  @Test
  fun `queue selection loads the episode and preserves listening preferences`() = runTest {
    val presenter = PlaybackPresenterImpl()

    presenter.testPlayer(this) {
      var player = awaitItem().expanded.player
      val nextEpisode = player.queue[1]
      player.onBookmark()
      player = awaitItem().expanded.player
      player.onDownload()
      player = awaitItem().expanded.player
      player.onPlayPause()
      player = awaitItem().expanded.player
      player.onSpeed(1.5f)
      player = awaitItem().expanded.player
      player.onSleep(30)
      player = awaitItem().expanded.player

      player.onEpisode(1)
      val model = awaitItem()
      player = model.expanded.player
      assertThat(player.title).isEqualTo(nextEpisode.title)
      assertThat(player.showTitle).isEqualTo(nextEpisode.showTitle)
      assertThat(player.durationSeconds).isEqualTo(nextEpisode.durationSeconds)
      assertThat(player.positionSeconds).isEqualTo(0)
      assertThat(player.activeChapterIndex).isEqualTo(0)
      assertThat(player.queue.size).isEqualTo(2)
      assertThat(player.queue.contains(nextEpisode)).isFalse()
      assertThat(player.isPlaying).isTrue()
      assertThat(player.isBookmarked).isFalse()
      assertThat(player.isDownloaded).isFalse()
      assertThat(player.speed).isEqualTo(1.5f)
      assertThat(player.sleepMinutes).isEqualTo(30)
      assertThat(model.collapsed.player).isSameInstanceAs(player)

      player.onSleep(null)
      player = awaitItem().expanded.player
      assertThat(player.sleepMinutes).isNull()

      player.onEpisode(-1)
      player.onEpisode(player.queue.size)
      expectNoEvents()
    }
  }

  private suspend fun PlaybackPresenterImpl.testPlayer(
    scope: TestScope,
    validate: suspend ReceiveTurbine<PlaybackPresenterImpl.Model>.() -> Unit,
  ) {
    ComposePresenter<Unit, PlaybackPresenterImpl.Model> { present(it) }
      .test(scope, validate = validate)
  }
}
