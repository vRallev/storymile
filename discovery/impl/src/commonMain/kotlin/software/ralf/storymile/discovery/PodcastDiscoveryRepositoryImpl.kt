package software.ralf.storymile.discovery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.http.HttpResponse

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PodcastDiscoveryRepositoryImpl(
  private val service: PodcastDiscoveryService,
  @ForScope(AppScope::class) private val coroutineScope: CoroutineScope,
) : PodcastDiscoveryRepository {
  private val recommendations = Request { service.popular() }
  private val lookups = MutableStateFlow<Map<String, Request>>(emptyMap())

  override val popular: StateFlow<State> = recommendations.state

  override fun lookup(id: String): StateFlow<State> = lookupRequest(id).state

  override fun refresh(id: String) {
    lookupRequest(id).refreshRequests.update { it + 1 }
  }

  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> =
    service.search(query)

  override fun refresh() {
    recommendations.refreshRequests.update { it + 1 }
  }

  private fun lookupRequest(id: String): Request =
    lookups
      .updateAndGet { if (id in it) it else it + (id to Request { service.lookup(id) }) }
      .getValue(id)

  private suspend fun load(request: suspend () -> HttpResponse<List<DiscoveredPodcast>>): State {
    return try {
      when (val response = request()) {
        is HttpResponse.Success -> State.Success(response.data)
        else -> State.Error
      }
    } catch (exception: Exception) {
      if (exception is CancellationException) {
        throw exception
      }
      State.Error
    }
  }

  private inner class Request(request: suspend () -> HttpResponse<List<DiscoveredPodcast>>) {
    val refreshRequests = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> by lazy {
      refreshRequests
        .transformLatest {
          while (true) {
            emit(State.Loading)
            emit(load(request))
            delay(1.hours)
          }
        }
        .stateIn(coroutineScope, SharingStarted.Lazily, State.Loading)
    }
  }
}
