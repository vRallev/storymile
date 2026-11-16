package software.ralf.storymile.playback

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.player.Player
import software.ralf.storymile.player.testing.FakePlayer
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueuedEpisode

class PlaybackPresenterImplTest {
  @Test
  fun `playback slots follow the selected episode`() = runTest {
    val fake = FakePlayer()
    val presenter: PlaybackPresenter = PlaybackPresenterImpl(fake)

    presenter.test(this) {
      awaitItem().assertPlayback(visible = false)

      fake.publish(Player.State(episode = episode("First episode")))
      awaitItem().assertPlayback(visible = true)

      fake.publish(Player.State())
      awaitItem().assertPlayback(visible = false)
    }
  }

  @Test
  fun `both layouts share episode metadata and live playback controls`() = runTest {
    val first = episode("First episode")
    val second = episode("Second episode")
    val third = episode("Third episode")
    val fake = FakePlayer()
    fake.publish(
      Player.State(
        episode = first,
        upcomingEpisodes = listOf(third, second),
        durationMillis = 120_000,
      ),
    )
    val presenter = PlaybackPresenterImpl(fake)

    presenter.testPlayer(this) {
      val initial = awaitItem()
      val bar = requireNotNull(initial.collapsed).player
      val expanded = requireNotNull(initial.expanded).player
      assertThat(bar).isSameInstanceAs(expanded)
      assertThat(expanded.title).isEqualTo(first.episode.title)
      assertThat(expanded.showTitle).isEqualTo(first.podcastTitle)
      assertThat(expanded.artworkUrl).isEqualTo(first.artworkUrl)
      assertThat(expanded.description).isEqualTo("Episode notes")
      assertThat(expanded.durationSeconds).isEqualTo(120)
      assertThat(expanded.canSeek).isFalse()
      assertThat(expanded.isPlaying).isFalse()
      assertThat(expanded.queue).isEqualTo(listOf(third, second))

      bar.onPlayPause()
      assertThat(fake.toggleCount).isEqualTo(1)
      fake.publish(
        fake.state.value.copy(
          positionMillis = 45_000,
          durationMillis = 110_000,
          isPlaying = true,
          isReady = true,
        ),
      )
      val playing = requireNotNull(awaitItem().expanded).player
      assertThat(playing.positionSeconds).isEqualTo(45)
      assertThat(playing.durationSeconds).isEqualTo(110)
      playing.onSkipBack()
      playing.onSkipForward()
      assertThat(fake.seekRequests).isEqualTo(listOf(30_000L, 75_000L))

      playing.onEpisode(playing.queue.first())
      assertThat(fake.playRequests).isEqualTo(listOf(third))
    }
  }

  private fun episode(title: String) =
    QueuedEpisode(
      feedUrl = "https://example.com/feed",
      podcastTitle = "Podcast",
      artworkUrl = "https://example.com/cover.jpg",
      episode =
        PodcastEpisode(
          id = title,
          title = title,
          description = "<p>Episode notes</p>",
          author = null,
          artworkUrl = null,
          publishedAt = null,
          duration = 2.minutes,
          enclosure =
            PodcastEnclosure(
              url = "https://example.com/audio.mp3",
              type = "audio/mpeg",
              length = null,
            ),
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
