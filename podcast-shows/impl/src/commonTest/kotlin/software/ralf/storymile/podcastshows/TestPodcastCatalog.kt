package software.ralf.storymile.podcastshows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.http.HttpResponse

internal class TestPodcastCatalog : PodcastDiscoveryRepository {
  private val lookups = mutableMapOf<String, MutableStateFlow<State>>()
  val lookedUp = mutableListOf<String>()
  val refreshedLookups = mutableListOf<String>()
  override val popular: StateFlow<State>
    field = MutableStateFlow<State>(State.Success(emptyList()))

  override fun refresh() = Unit

  override fun lookup(id: String): StateFlow<State> {
    lookedUp += id
    return lookups.getOrPut(id) { MutableStateFlow(State.Success(emptyList())) }
  }

  override fun refresh(id: String) {
    refreshedLookups += id
    updateLookup(id, State.Loading)
  }

  fun updateLookup(id: String, state: State) {
    lookups.getOrPut(id) { MutableStateFlow(State.Success(emptyList())) }.value = state
  }

  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> =
    error("Unexpected search")
}
