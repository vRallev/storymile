package software.ralf.storymile.playback

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.playlist.EpisodeId
import software.ralf.storymile.playlist.Playlist
import software.ralf.storymile.playlist.PlaylistId
import software.ralf.storymile.playlist.PlaylistName

class PlaybackPresenterImplTest {
  private val PlaybackPresenterImpl.Model.player: PlaybackPresenterImpl.PlayerModel
    get() = requireNotNull(expanded).player

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `playback is absent until a playlist contains episodes`() = runTest {
    val repository = TestPlaylistRepository(emptyList())
    val presenter: PlaybackPresenter = PlaybackPresenterImpl(repository, TestPlayer())

    presenter.test(this) {
      awaitItem().assertPlayback(visible = false)

      val empty = playlist().copy(episodeIds = emptyList())
      repository.update(listOf(empty))
      runCurrent()
      expectNoEvents()

      val populated = playlist().copy(id = PlaylistId("second"))
      repository.update(listOf(empty, populated))
      awaitItem().assertPlayback(visible = true)

      repository.update(listOf(empty))
      awaitItem().assertPlayback(visible = false)
    }
  }

  @Test
  fun `playback state survives unavailable content`() = runTest {
    val repository = TestPlaylistRepository(listOf(playlist()))
    val presenter = PlaybackPresenterImpl(repository, TestPlayer())

    presenter.testPlayer(this) {
      awaitItem().player.onSeek(123)
      awaitItem().player.onSpeed(1.5f)
      awaitItem().player.onPlayPause()
      assertThat(awaitItem().player.isPlaying).isFalse()

      repository.update(emptyList())
      awaitItem().assertPlayback(visible = false)

      repository.update(listOf(playlist()))
      val model = awaitItem()
      model.assertPlayback(visible = true)
      assertThat(model.player.positionSeconds).isEqualTo(123)
      assertThat(model.player.speed).isEqualTo(1.5f)
      assertThat(model.player.isPlaying).isFalse()
    }
  }

  @Test
  fun `collapsed and expanded controls share playback state`() = runTest {
    val presenter = PlaybackPresenterImpl(TestPlaylistRepository(listOf(playlist())), TestPlayer())

    presenter.testPlayer(this) {
      var model = awaitItem()
      assertThat(requireNotNull(model.collapsed).player).isSameInstanceAs(model.player)
      assertThat(model.player.activeChapterIndex).isEqualTo(2)

      requireNotNull(model.collapsed).player.onPlayPause()
      model = awaitItem()
      assertThat(model.player.isPlaying).isFalse()

      model.player.onBookmark()
      model = awaitItem()
      assertThat(requireNotNull(model.collapsed).player.isBookmarked).isTrue()

      model.player.onDownload()
      model = awaitItem()
      assertThat(requireNotNull(model.collapsed).player.isDownloaded).isTrue()

      model.player.onSpeed(1.5f)
      model = awaitItem()
      model.player.onSleep(30)
      model = awaitItem()
      requireNotNull(model.collapsed).player.onPlayPause()
      model = awaitItem()
      assertThat(model.player.isPlaying).isTrue()
      assertThat(model.player.isBookmarked).isTrue()
      assertThat(model.player.isDownloaded).isTrue()
      assertThat(model.player.speed).isEqualTo(1.5f)
      assertThat(model.player.sleepMinutes).isEqualTo(30)
      assertThat(requireNotNull(model.collapsed).player).isSameInstanceAs(model.player)
    }
  }

  @Test
  fun `seeking and skips stay within the current episode`() = runTest {
    val presenter = PlaybackPresenterImpl(TestPlaylistRepository(listOf(playlist())), TestPlayer())

    presenter.testPlayer(this) {
      var player = awaitItem().player
      player.onSkipBack()
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(739)

      player.onSkipForward()
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(769)

      player.onSeek(5)
      player = awaitItem().player
      player.onSkipBack()
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(0)
      assertThat(player.activeChapterIndex).isEqualTo(0)

      player.onSeek(player.durationSeconds - 5)
      player = awaitItem().player
      player.onSkipForward()
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(player.durationSeconds)
      assertThat(player.activeChapterIndex).isEqualTo(player.chapters.lastIndex)

      player.onSeek(-100)
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(0)

      player.onSeek(Int.MAX_VALUE)
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(player.durationSeconds)
    }
  }

  @Test
  fun `chapter selection and seeking keep the active chapter in sync`() = runTest {
    val presenter = PlaybackPresenterImpl(TestPlaylistRepository(listOf(playlist())), TestPlayer())

    presenter.testPlayer(this) {
      var player = awaitItem().player
      player.onChapter(4)
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(player.chapters[4].startSeconds)
      assertThat(player.activeChapterIndex).isEqualTo(4)

      player.onSeek(player.chapters[4].startSeconds - 1)
      player = awaitItem().player
      assertThat(player.activeChapterIndex).isEqualTo(3)

      player.onChapter(-1)
      player.onChapter(player.chapters.size)
      expectNoEvents()
    }
  }

  @Test
  fun `queue selection loads the episode and preserves listening preferences`() = runTest {
    val presenter = PlaybackPresenterImpl(TestPlaylistRepository(listOf(playlist())), TestPlayer())

    presenter.testPlayer(this) {
      var player = awaitItem().player
      val nextEpisode = player.queue[1]
      player.onBookmark()
      player = awaitItem().player
      player.onDownload()
      player = awaitItem().player
      player.onPlayPause()
      player = awaitItem().player
      player.onSpeed(1.5f)
      player = awaitItem().player
      player.onSleep(30)
      player = awaitItem().player

      player.onEpisode(1)
      val model = awaitItem()
      player = model.player
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
      assertThat(requireNotNull(model.collapsed).player).isSameInstanceAs(player)

      player.onSleep(null)
      player = awaitItem().player
      assertThat(player.sleepMinutes).isNull()

      player.onEpisode(-1)
      player.onEpisode(player.queue.size)
      expectNoEvents()
    }
  }

  private fun playlist(): Playlist =
    Playlist(PlaylistId("playlist"), PlaylistName("Favorites"), listOf(EpisodeId("episode")))

  private fun PlaybackPresenter.Model.assertPlayback(visible: Boolean) {
    if (visible) {
      assertThat(collapsed).isNotNull()
      assertThat(expanded).isNotNull()
    } else {
      assertThat(collapsed).isNull()
      assertThat(expanded).isNull()
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
