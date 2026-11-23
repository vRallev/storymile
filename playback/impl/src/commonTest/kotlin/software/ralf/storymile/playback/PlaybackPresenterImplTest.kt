package software.ralf.storymile.playback

import androidx.compose.runtime.SideEffect
import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.player.Player
import software.ralf.storymile.player.testing.FakePlayer
import software.ralf.storymile.player.testing.PreviewTestPlayer
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueuedEpisode

class PlaybackPresenterImplTest {
  private val PlaybackPresenterImpl.Model.player: PlaybackPresenterImpl.PlayerModel
    get() = requireNotNull(expanded).player

  @Test
  fun `playback is absent when the player has no selected episode`() = runTest {
    val player = PreviewTestPlayer()
    val presenter: PlaybackPresenter = PlaybackPresenterImpl(player)

    presenter.test(this) {
      awaitItem().assertPlayback(visible = false)

      player.showPreview()
      awaitItem().assertPlayback(visible = true)

      player.hidePreview()
      awaitItem().assertPlayback(visible = false)
    }
  }

  @Test
  fun `collapsed and expanded controls share playback state`() = runTest {
    val presenter = PlaybackPresenterImpl(PreviewTestPlayer().apply { showPreview() })

    presenter.testPlayer(this) {
      var model = awaitItem()
      assertThat(requireNotNull(model.collapsed).player).isSameInstanceAs(model.player)

      requireNotNull(model.collapsed).player.onPlayPause()
      model = awaitItem()
      assertThat(model.player.isPlaying).isFalse()

      model.player.onSpeed(1.5f)
      model = awaitItem()
      requireNotNull(model.collapsed).player.onPlayPause()
      model = awaitItem()
      assertThat(model.player.isPlaying).isTrue()
      assertThat(model.player.speed).isEqualTo(1.5f)
      assertThat(requireNotNull(model.collapsed).player).isSameInstanceAs(model.player)
    }
  }

  @Test
  fun `seeking and skips stay within the current episode`() = runTest {
    val presenter = PlaybackPresenterImpl(PreviewTestPlayer().apply { showPreview() })

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

      player.onSeek(player.durationSeconds - 5)
      player = awaitItem().player
      player.onSkipForward()
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(player.durationSeconds)

      player.onSeek(-100)
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(0)

      player.onSeek(Int.MAX_VALUE)
      player = awaitItem().player
      assertThat(player.positionSeconds).isEqualTo(player.durationSeconds)
    }
  }

  @Test
  fun `subsecond progress keeps the current model and skips from the latest audio position`() =
    runTest {
      val player = FakePlayer()
      val initial = Player.State(episode = queued("Current"), positionMillis = 1_100)
      player.publish(initial)
      val presenter = PlaybackPresenterImpl(player)
      var presentations = 0
      ComposePresenter<Unit, PlaybackPresenterImpl.Model> {
          val model = presenter.present(it)
          SideEffect { presentations++ }
          model
        }
        .test(this) {
          val model = awaitItem().player
          val beforeTick = presentations
          player.publish(initial.copy(positionMillis = 1_950))
          runCurrent()
          expectNoEvents()
          assertThat(presentations).isEqualTo(beforeTick)
          model.onSkipForward()
          assertThat(player.seekRequests).isEqualTo(listOf(31_950L))

          player.publish(initial.copy(positionMillis = 1_990, playWhenReady = true))
          assertThat(awaitItem().player.isPlaying).isTrue()
          player.publish(initial.copy(positionMillis = 2_000, playWhenReady = true))
          assertThat(awaitItem().player.positionSeconds).isEqualTo(2)
        }
    }

  @Test
  fun `queue selection loads the episode and preserves listening preferences`() = runTest {
    val presenter = PlaybackPresenterImpl(PreviewTestPlayer().apply { showPreview() })

    presenter.testPlayer(this) {
      var player = awaitItem().player
      val nextEpisode = player.queue[1]
      player.onPlayPause()
      player = awaitItem().player
      player.onSpeed(1.5f)
      player = awaitItem().player

      player.onEpisode(1)
      val model = awaitItem()
      player = model.player
      assertThat(player.title).isEqualTo(nextEpisode.title)
      assertThat(player.showTitle).isEqualTo(nextEpisode.showTitle)
      assertThat(player.durationSeconds).isEqualTo(nextEpisode.durationSeconds)
      assertThat(player.positionSeconds).isEqualTo(0)
      assertThat(player.queue.size).isEqualTo(2)
      assertThat(player.queue.contains(nextEpisode)).isFalse()
      assertThat(player.isPlaying).isTrue()
      assertThat(player.speed).isEqualTo(1.5f)
      assertThat(requireNotNull(model.collapsed).player).isSameInstanceAs(player)

      player.onEpisode(-1)
      player.onEpisode(player.queue.size)
      expectNoEvents()
    }
  }

  @Test
  fun `playback presents Library Queue metadata and plays the chosen entry`() = runTest {
    val player = FakePlayer()
    val presenter = PlaybackPresenterImpl(player)

    presenter.testPlayer(this) {
      awaitItem().assertPlayback(visible = false)

      val current = queued("Current episode")
      val next = queued("Next episode")
      player.publish(Player.State(episode = current, upcomingEpisodes = listOf(next)))
      val model = awaitItem()
      model.assertPlayback(visible = true)
      assertThat(requireNotNull(model.collapsed).player).isSameInstanceAs(model.player)
      assertThat(model.player.title).isEqualTo(current.episode.title)
      assertThat(model.player.showTitle).isEqualTo(current.podcastTitle)
      assertThat(model.player.artworkUrl).isEqualTo(current.artworkUrl)
      assertThat(model.player.description).isEqualTo("Notes about Current episode")
      assertThat(model.player.queue.map { it.title }).isEqualTo(listOf(next.episode.title))
      model.player.onEpisode(0)
      assertThat(player.playRequests).isEqualTo(listOf(next))

      player.publish(Player.State())
      awaitItem().assertPlayback(visible = false)
    }
  }

  private fun queued(title: String) =
    QueuedEpisode(
      feedUrl = "https://example.com/feed",
      podcastTitle = "Podcast",
      artworkUrl = "https://example.com/cover.png",
      episode =
        PodcastEpisode(
          id = title,
          title = title,
          description = "<p>Notes about $title</p>",
          author = null,
          artworkUrl = null,
          publishedAt = null,
          duration = null,
          enclosure =
            PodcastEnclosure(url = "https://example.com/audio.mp3", type = null, length = null),
        ),
    )

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
