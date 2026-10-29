package software.ralf.storymile.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.episodedetails.testing.FakeEpisodeDetailsPresenterFactory
import software.ralf.storymile.podcast.PodcastFeed

internal fun testShowsPresenter() =
  LibraryShowsPresenter(
    database = TestPodcastDatabase(),
    episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
  )

internal class TestPodcastDatabase : PodcastDatabase {
  val saved: StateFlow<List<PodcastFeed>>
    field = MutableStateFlow(emptyList())

  var readFailure: Exception? = null
  var writeFailure: Exception? = null
  var finishWrite: CompletableDeferred<Unit>? = null

  override val podcasts: Flow<List<PodcastFeed>> = flow {
    readFailure?.let { throw it }
    emitAll(saved)
  }

  override suspend fun insert(podcast: PodcastFeed) {
    saved.value =
      (saved.value.filterNot { it.feedUrl == podcast.feedUrl } + podcast).sortedBy { it.feedUrl }
  }

  override suspend fun delete(feedUrl: String) {
    finishWrite?.await()
    writeFailure?.let { throw it }
    saved.value = saved.value.filterNot { it.feedUrl == feedUrl }
  }
}
