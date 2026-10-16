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
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.http.HttpResponse

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PodcastDiscoveryRepositoryImpl(
  private val service: PodcastDiscoveryService,
  @ForScope(AppScope::class) coroutineScope: CoroutineScope,
) : PodcastDiscoveryRepository {
  private val refreshRequests = MutableStateFlow(0)

  @OptIn(ExperimentalCoroutinesApi::class)
  override val popular: StateFlow<State> =
    refreshRequests
      .transformLatest {
        while (true) {
          emit(State.Loading)
          emit(load())
          delay(1.hours)
        }
      }
      .stateIn(coroutineScope, SharingStarted.Lazily, State.Loading)

  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> =
    service.search(query)

  override fun refresh() {
    refreshRequests.update { it + 1 }
  }

  private suspend fun load(): State {
    return try {
      when (val response = service.popular()) {
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
}
