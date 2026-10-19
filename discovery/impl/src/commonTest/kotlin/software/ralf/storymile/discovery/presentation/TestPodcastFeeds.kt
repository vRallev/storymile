package software.ralf.storymile.discovery.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.podcast.PodcastFeedRepository

class TestPodcastFeeds : PodcastFeedRepository {
  private val states = mutableMapOf<String, MutableStateFlow<PodcastFeedRepository.State>>()
  val requested = mutableListOf<String>()
  val refreshed = mutableListOf<String>()

  override fun feed(feedUrl: String): StateFlow<PodcastFeedRepository.State> {
    requested += feedUrl
    return state(feedUrl)
  }

  override fun refresh(feedUrl: String) {
    refreshed += feedUrl
    update(feedUrl, PodcastFeedRepository.State.Loading)
  }

  fun update(url: String, value: PodcastFeedRepository.State) {
    state(url).value = value
  }

  fun isObserved(url: String): Boolean = state(url).subscriptionCount.value > 0

  private fun state(url: String) =
    states.getOrPut(url) { MutableStateFlow(PodcastFeedRepository.State.Loading) }
}
