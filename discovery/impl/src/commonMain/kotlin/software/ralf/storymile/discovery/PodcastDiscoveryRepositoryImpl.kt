package software.ralf.storymile.discovery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.ralf.storymile.http.HttpResponse

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PodcastDiscoveryRepositoryImpl(
  private val service: PodcastDiscoveryService,
  private val timeSource: TimeSource = TimeSource.Monotonic,
) : PodcastDiscoveryRepository {
  private val mutex = Mutex()
  private var cached: CachedRecommendations? = null

  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> =
    service.search(query)

  override suspend fun popular(): HttpResponse<List<DiscoveredPodcast>> = mutex.withLock {
    cached?.let {
      if (it.fetchedAt.elapsedNow() < 1.hours) {
        return@withLock it.response
      }
    }

    val response = service.popular()
    currentCoroutineContext().ensureActive()
    if (response is HttpResponse.Success) {
      cached = CachedRecommendations(response, timeSource.markNow())
    }
    response
  }

  private data class CachedRecommendations(
    val response: HttpResponse.Success<List<DiscoveredPodcast>>,
    val fetchedAt: TimeMark,
  )
}
