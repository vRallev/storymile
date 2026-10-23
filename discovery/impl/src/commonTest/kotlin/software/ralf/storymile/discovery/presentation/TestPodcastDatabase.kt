package software.ralf.storymile.discovery.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.podcast.PodcastFeed

class TestPodcastDatabase : PodcastDatabase {
  val saved: StateFlow<List<PodcastFeed>>
    field = MutableStateFlow(emptyList())

  var readFailure: Exception? = null
  var writeFailure: Exception? = null
  var finishWrite: CompletableDeferred<Unit>? = null
  var writes = 0
    private set

  override val podcasts: Flow<List<PodcastFeed>> = flow {
    readFailure?.let { throw it }
    emitAll(saved)
  }

  override suspend fun insert(podcast: PodcastFeed) {
    beforeWrite()
    saved.value =
      (saved.value.filterNot { it.feedUrl == podcast.feedUrl } + podcast).sortedBy { it.feedUrl }
  }

  override suspend fun delete(feedUrl: String) {
    beforeWrite()
    saved.value = saved.value.filterNot { it.feedUrl == feedUrl }
  }

  private suspend fun beforeWrite() {
    writes++
    finishWrite?.await()
    writeFailure?.let { throw it }
  }
}
