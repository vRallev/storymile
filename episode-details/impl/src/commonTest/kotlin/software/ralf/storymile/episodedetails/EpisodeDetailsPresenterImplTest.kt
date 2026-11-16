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
import software.ralf.storymile.podcast.SavedEpisode
import software.ralf.storymile.podcast.SavedEpisodeRepository

class EpisodeDetailsPresenterImplTest {
  private val details =
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

  @Test
  fun `queue button adds episode metadata and removes only the selected episode`() = runTest {
    val queue = RecordingQueue()
    val savedEpisodes = FakeSavedEpisodes()
    val queuedEpisode =
      QueuedEpisode(
        feedUrl = details.feedUrl,
        podcastTitle = details.podcastTitle,
        artworkUrl = details.artworkUrl,
        episode = details.episode,
      )
    val otherFeedEpisode = queuedEpisode.copy(feedUrl = "https://example.test/other-feed")
    val presenter =
      EpisodeDetailsPresenterImpl(details = details, queue = queue, savedEpisodes = savedEpisodes)
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

  @Test
  fun `save button stores and removes the selected episode independently of other feeds`() =
    runTest {
      val savedEpisodes = FakeSavedEpisodes()
      val selected =
        SavedEpisode(
          feedUrl = details.feedUrl,
          podcastTitle = details.podcastTitle,
          artworkUrl = details.artworkUrl,
          episode = details.episode,
        )
      val otherFeedEpisode = selected.copy(feedUrl = "https://example.test/other-feed")
      val presenter =
        EpisodeDetailsPresenterImpl(
          details = details,
          queue = RecordingQueue(),
          savedEpisodes = savedEpisodes,
        )
      presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(this) {
        val loading = awaitItem()
        assertThat(loading.saveEnabled).isFalse()
        loading.onToggleSave()
        savedEpisodes.load(listOf(otherFeedEpisode))

        val ready = awaitItem()
        assertThat(ready.saveEnabled).isTrue()
        assertThat(ready.isSaved).isFalse()

        ready.onToggleSave()
        val saved = awaitItem()
        assertThat(saved.isSaved).isTrue()
        assertThat(checkNotNull(savedEpisodes.episodes.value))
          .containsExactly(selected, otherFeedEpisode)

        saved.onToggleSave()
        assertThat(awaitItem().isSaved).isFalse()
        assertThat(checkNotNull(savedEpisodes.episodes.value)).containsExactly(otherFeedEpisode)
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

  private class FakeSavedEpisodes : SavedEpisodeRepository {
    override val episodes: StateFlow<List<SavedEpisode>?>
      field = MutableStateFlow<List<SavedEpisode>?>(null)

    fun load(episodes: List<SavedEpisode>) {
      this.episodes.value = episodes
    }

    override fun save(episode: SavedEpisode) {
      episodes.value = listOf(episode) + checkNotNull(episodes.value)
    }

    override fun remove(episode: SavedEpisode) {
      episodes.value = checkNotNull(episodes.value) - episode
    }
  }
}
