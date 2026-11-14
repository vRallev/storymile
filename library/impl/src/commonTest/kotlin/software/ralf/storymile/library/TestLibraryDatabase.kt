package software.ralf.storymile.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.podcast.PodcastFeed

internal class TestLibraryDatabase : PodcastDatabase {
  override val podcasts: Flow<List<PodcastFeed>>
    field = MutableStateFlow(emptyList())

  override suspend fun insert(podcast: PodcastFeed) {
    podcasts.value = podcasts.value.filterNot { it.feedUrl == podcast.feedUrl } + podcast
  }

  override suspend fun delete(feedUrl: String) {
    podcasts.value = podcasts.value.filterNot { it.feedUrl == feedUrl }
  }
}
