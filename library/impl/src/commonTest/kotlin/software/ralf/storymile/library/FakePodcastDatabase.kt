package software.ralf.storymile.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.podcast.PodcastFeed

internal class FakePodcastDatabase : PodcastDatabase {
  private val shows = MutableStateFlow<List<PodcastFeed>>(emptyList())
  var readFailure: Exception? = null

  override val podcasts: Flow<List<PodcastFeed>> = flow {
    readFailure?.let { throw it }
    emitAll(shows)
  }

  override suspend fun insert(podcast: PodcastFeed) {
    shows.value =
      (shows.value.filterNot { it.feedUrl == podcast.feedUrl } + podcast).sortedBy { it.feedUrl }
  }

  override suspend fun delete(feedUrl: String) {
    shows.value = shows.value.filterNot { it.feedUrl == feedUrl }
  }
}
