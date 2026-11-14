package software.ralf.storymile.discovery.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.SavedShowRepository

class TestSavedShowRepository : SavedShowRepository {
  override val shows: StateFlow<List<PodcastFeed>?>
    field = MutableStateFlow<List<PodcastFeed>?>(emptyList())

  fun publish(shows: List<PodcastFeed>?) {
    this.shows.value = shows
  }

  override fun save(show: PodcastFeed) {
    shows.update { current ->
      (current.orEmpty().filterNot { it.feedUrl == show.feedUrl } + show).sortedBy { it.feedUrl }
    }
  }

  override fun remove(feedUrl: String) {
    shows.update { current -> current.orEmpty().filterNot { it.feedUrl == feedUrl } }
  }
}
