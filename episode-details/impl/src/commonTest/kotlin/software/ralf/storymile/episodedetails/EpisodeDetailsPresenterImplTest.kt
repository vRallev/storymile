package software.ralf.storymile.episodedetails

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
  fun `add to queue saves the selected episode with its show metadata`() = runTest {
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
    val presenter = EpisodeDetailsPresenterImpl(details = details, queue = queue)
    presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(this) {
      val model = awaitItem()
      assertThat(queue.saved).isEmpty()
      model.onAddToQueue()
      assertThat(queue.saved.single())
        .isEqualTo(
          QueuedEpisode(
            feedUrl = details.feedUrl,
            podcastTitle = details.podcastTitle,
            artworkUrl = details.artworkUrl,
            episode = details.episode,
          ),
        )
    }
  }

  private class RecordingQueue : QueueRepository {
    val saved = mutableListOf<QueuedEpisode>()
    override val episodes: StateFlow<List<QueuedEpisode>?> = MutableStateFlow(null)

    override fun save(episode: QueuedEpisode) {
      saved += episode
    }

    override fun remove(feedUrl: String, episodeId: String): Unit = error("Unexpected remove")
  }
}
