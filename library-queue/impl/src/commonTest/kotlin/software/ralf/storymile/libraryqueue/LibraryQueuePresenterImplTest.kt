package software.ralf.storymile.libraryqueue

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode

class LibraryQueuePresenterImplTest {
  @Test
  fun `removing the opened episode selects the first remaining entry`() = runTest {
    val episodes = listOf(episode("first"), episode("middle"), episode("last"))
    episodes.indices.forEach { index ->
      val queue = TestQueue(episodes)
      val presenter = LibraryQueuePresenterImpl(queue, DetailsFactory)
      presenter.withRendererModel().test(this) {
        val list = awaitItem().list
        val selected = checkNotNull(list.episodes)[index]
        list.onSelect(selected)
        val opened = awaitItem()
        assertThat(opened.openedEpisode).isEqualTo(DetailsModel(selected.key))

        opened.list.onRemove(selected)
        val removed = awaitItem()
        val remaining = checkNotNull(removed.list.episodes)
        assertThat(remaining.map { it.episode.id })
          .isEqualTo(
            episodes.filterIndexed { position, _ -> position != index }.map { it.episode.id },
          )
        assertThat(removed.openedEpisode).isNull()
        assertThat(removed.list.selection?.episodeKey).isEqualTo(remaining.first().key)
        assertThat(removed.list.selection?.content).isEqualTo(DetailsModel(remaining.first().key))
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  @Test
  fun `an external removal of the last opened episode clears details`() = runTest {
    val episode = episode("last")
    val queue = TestQueue(listOf(episode))
    val presenter = LibraryQueuePresenterImpl(queue, DetailsFactory)
    presenter.withRendererModel().test(this) {
      val list = awaitItem().list
      list.onSelect(checkNotNull(list.episodes).single())
      awaitItem()

      queue.load(emptyList())
      val empty = awaitItem()
      assertThat(empty.isEmpty).isTrue()
      assertThat(empty.openedEpisode).isNull()
      assertThat(empty.list.selection).isNull()

      // Re-adding the episode must not restore the removed navigation destination.
      queue.load(listOf(episode))
      val restored = awaitItem()
      assertThat(restored.openedEpisode).isNull()
      assertThat(restored.list.selection?.episodeKey)
        .isEqualTo(checkNotNull(restored.list.episodes).single().key)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `loading and removing another feed preserve the opened episode`() = runTest {
    val first = episode("same-id")
    val second = first.copy(feedUrl = "https://example.test/other-feed")
    val queue = TestQueue(listOf(first, second))
    val presenter = LibraryQueuePresenterImpl(queue, DetailsFactory)
    presenter.withRendererModel().test(this) {
      val list = awaitItem().list
      val selected = checkNotNull(list.episodes).last()
      list.onSelect(selected)
      awaitItem()

      queue.load(null)
      val loading = awaitItem()
      assertThat(loading.list.episodes).isNull()
      assertThat(loading.openedEpisode).isEqualTo(DetailsModel(selected.key))

      queue.load(listOf(first, second))
      val loaded = awaitItem()
      loaded.list.onRemove(checkNotNull(loaded.list.episodes).first())
      val removed = awaitItem()
      assertThat(removed.openedEpisode).isEqualTo(DetailsModel(selected.key))
      assertThat(removed.list.selection?.episodeKey).isEqualTo(selected.key)
    }
  }

  private fun LibraryQueuePresenterImpl.withRendererModel() =
    ComposePresenter<Unit, LibraryQueuePresenterImpl.Model> { present(it) }

  private fun episode(id: String): QueuedEpisode =
    QueuedEpisode(
      feedUrl = "https://example.test/feed",
      podcastTitle = "The Show",
      artworkUrl = null,
      episode =
        PodcastEpisode(
          id = id,
          title = id,
          description = null,
          author = null,
          artworkUrl = null,
          publishedAt = null,
          duration = null,
          enclosure =
            PodcastEnclosure(
              url = "https://example.test/$id.mp3",
              type = null,
              length = null,
            ),
        ),
    )

  private class TestQueue(initial: List<QueuedEpisode>) : QueueRepository {
    override val episodes: StateFlow<List<QueuedEpisode>?>
      field = MutableStateFlow<List<QueuedEpisode>?>(initial)

    fun load(episodes: List<QueuedEpisode>?) {
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

    override fun removeWithUndo(episode: QueuedEpisode, onRemoved: (undo: () -> Unit) -> Unit) {
      val before = episodes.value
      remove(episode)
      if (episodes.value != before) {
        onRemoved { error("Unexpected Undo") }
      }
    }

    override fun move(episode: QueuedEpisode, target: QueuedEpisode) = Unit
  }

  private object DetailsFactory : EpisodeDetailsPresenter.Factory {
    override fun create(details: EpisodeDetails): EpisodeDetailsPresenter<*> =
      object : EpisodeDetailsPresenter<DetailsModel> {
        @Composable override fun present(input: Unit): DetailsModel = DetailsModel(details.key)
      }
  }

  private data class DetailsModel(val episodeKey: String) : BaseModel
}
