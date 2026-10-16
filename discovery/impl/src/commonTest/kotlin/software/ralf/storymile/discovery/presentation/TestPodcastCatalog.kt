package software.ralf.storymile.discovery.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.http.HttpResponse

class TestPodcastCatalog : PodcastDiscoveryRepository {
  private val lookups = mutableMapOf<String, MutableStateFlow<State>>()
  val lookedUp = mutableListOf<String>()
  val refreshedLookups = mutableListOf<String>()
  var response: HttpResponse<List<DiscoveredPodcast>> = HttpResponse.Success(emptyList(), 200)
  val pending = mutableMapOf<String, CompletableDeferred<HttpResponse<List<DiscoveredPodcast>>>>()
  val queries = mutableListOf<String>()
  var refreshRequests = 0
  override val popular: StateFlow<State>
    field = MutableStateFlow<State>(State.Success(emptyList()))

  fun updatePopular(state: State) {
    popular.value = state
  }

  override fun refresh() {
    refreshRequests++
    popular.value = State.Loading
  }

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

  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
    queries += query
    return pending[query]?.await() ?: response
  }
}
