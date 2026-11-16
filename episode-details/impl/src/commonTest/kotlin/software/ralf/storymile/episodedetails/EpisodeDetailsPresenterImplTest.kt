package software.ralf.storymile.episodedetails

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode

class EpisodeDetailsPresenterImplTest {
  @Test
  fun `queue button adds episode metadata and removes only the selected episode`() = runTest {
    val queue = RecordingQueue()
    val details =
      EpisodeDetails(
        feedUrl = "https://example.test/feed",
        podcastTitle = "The Show",
        artworkUrl = "https://example.test/show.png",
        episode =
          PodcastEpisode(
            id = "episode-id",
            title = "The Episode",
            description = null,
            author = null,
            artworkUrl = null,
            publishedAt = null,
            duration = null,
            enclosure =
              PodcastEnclosure(
                url = "https://example.test/episode.mp3",
                type = null,
                length = null,
              ),
          ),
      )
    val queuedEpisode =
      QueuedEpisode(
        feedUrl = details.feedUrl,
        podcastTitle = details.podcastTitle,
        artworkUrl = details.artworkUrl,
        episode = details.episode,
      )
    val otherFeedEpisode = queuedEpisode.copy(feedUrl = "https://example.test/other-feed")
    val presenter = EpisodeDetailsPresenterImpl(details = details, queue = queue)
    presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(this) {
      assertThat(awaitItem().queueEnabled).isFalse()
      queue.load(listOf(otherFeedEpisode))
      val ready = awaitItem()
      assertThat(ready.queueEnabled).isTrue()
      assertThat(ready.isQueued).isFalse()

      ready.onToggleQueue()
      val queued = awaitItem()
      assertThat(queued.isQueued).isTrue()
      assertThat(checkNotNull(queue.episodes.value))
        .containsExactly(otherFeedEpisode, queuedEpisode)

      queued.onToggleQueue()
      assertThat(awaitItem().isQueued).isFalse()
      assertThat(checkNotNull(queue.episodes.value)).containsExactly(otherFeedEpisode)
    }
  }

  private class RecordingQueue : QueueRepository {
    override val episodes: StateFlow<List<QueuedEpisode>?>
      field = MutableStateFlow<List<QueuedEpisode>?>(null)

    fun load(episodes: List<QueuedEpisode>) {
      this.episodes.value = episodes
    }

    override fun save(episode: QueuedEpisode) {
      episodes.value = checkNotNull(episodes.value) + episode
    }

    override fun remove(episode: QueuedEpisode) {
      episodes.value =
        checkNotNull(episodes.value).filterNot {
          it.feedUrl == episode.feedUrl && it.episode.id == episode.episode.id
        }
    }

    override fun move(episode: QueuedEpisode, target: QueuedEpisode) =
      error("Unexpected queue reorder")
  }
}
