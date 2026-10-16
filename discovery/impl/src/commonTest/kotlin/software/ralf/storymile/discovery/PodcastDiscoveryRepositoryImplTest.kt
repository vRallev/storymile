package software.ralf.storymile.discovery

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpResponse

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastDiscoveryRepositoryImplTest {
  @Test
  fun `recommendations expire after one hour without extending on cache hits`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, testScheduler.timeSource)
    val original = repository.popular()
    service.response = response("updated")

    advanceTimeBy(30.minutes)
    assertThat(repository.popular()).isEqualTo(original)
    advanceTimeBy(30.minutes - 1.milliseconds)
    assertThat(repository.popular()).isEqualTo(original)
    assertThat(service.popularRequests).isEqualTo(1)

    advanceTimeBy(1.milliseconds)
    assertThat(repository.popular()).isEqualTo(service.response)
    assertThat(service.popularRequests).isEqualTo(2)
    assertThat(repository.popular()).isEqualTo(service.response)
    assertThat(service.popularRequests).isEqualTo(2)
  }

  @Test
  fun `cache lifetime starts when the fetch completes and includes empty results`() = runTest {
    val service = TestService()
    service.latency = 1.minutes
    service.response = HttpResponse.Success(emptyList(), 200)
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, testScheduler.timeSource)
    val original = repository.popular()

    advanceTimeBy(1.hours - 1.milliseconds)
    service.response = response("updated")
    assertThat(repository.popular()).isEqualTo(original)
    assertThat(service.popularRequests).isEqualTo(1)

    advanceTimeBy(1.milliseconds)
    assertThat(repository.popular()).isEqualTo(service.response)
    assertThat(service.popularRequests).isEqualTo(2)
  }

  @Test
  fun `failed fetches can be retried immediately`() = runTest {
    val service = TestService()
    service.response = HttpResponse.Error.Server(503)
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, testScheduler.timeSource)

    assertThat(repository.popular()).isEqualTo(service.response)
    service.response = response("recovered")
    assertThat(repository.popular()).isEqualTo(service.response)
    assertThat(repository.popular()).isEqualTo(service.response)
    assertThat(service.popularRequests).isEqualTo(2)
  }

  @Test
  fun `failed refresh returns the failure and leaves expired results unusable`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, testScheduler.timeSource)
    repository.popular()
    advanceTimeBy(1.hours)
    service.response = HttpResponse.Error.Server(503)

    assertThat(repository.popular()).isEqualTo(service.response)
    service.response = response("recovered")
    assertThat(repository.popular()).isEqualTo(service.response)
    assertThat(service.popularRequests).isEqualTo(3)
  }

  @Test
  fun `concurrent callers share a successful fetch on first load and expiry`() = runTest {
    val service = TestService()
    service.latency = 1.seconds
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, testScheduler.timeSource)

    repeat(2) { refresh ->
      service.response = response("result-$refresh")
      val results = List(5) { async { repository.popular() } }.awaitAll()
      assertThat(results).isEqualTo(List(5) { service.response })
      assertThat(service.popularRequests).isEqualTo(refresh + 1)
      advanceTimeBy(1.hours)
    }
  }

  @Test
  fun `cancelling a fetch releases waiting callers without caching the cancelled request`() =
    runTest {
      val service = TestService()
      val pending = CompletableDeferred<Unit>()
      service.pending = pending
      val repository: PodcastDiscoveryRepository =
        PodcastDiscoveryRepositoryImpl(service, testScheduler.timeSource)
      val first = async { repository.popular() }
      runCurrent()
      val second = async { repository.popular() }
      runCurrent()
      assertThat(service.popularRequests).isEqualTo(1)

      first.cancelAndJoin()
      pending.complete(Unit)
      assertThat(first.isCancelled).isTrue()
      assertThat(second.await()).isEqualTo(service.response)
      assertThat(service.popularRequests).isEqualTo(2)
    }

  @Test
  fun `searches stay uncached and do not replace recommendations`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, testScheduler.timeSource)
    val recommendations = repository.popular()
    service.response = response("first-search")
    assertThat(repository.search("science")).isEqualTo(service.response)
    service.response = response("second-search")
    assertThat(repository.search("science")).isEqualTo(service.response)
    assertThat(service.queries).containsExactly("science", "science")
    assertThat(repository.popular()).isEqualTo(recommendations)
    assertThat(service.popularRequests).isEqualTo(1)
  }

  private fun response(id: String): HttpResponse<List<DiscoveredPodcast>> =
    HttpResponse.Success(listOf(DiscoveredPodcast(id, "Podcast $id", null, null, null, null)), 200)

  private class TestService : PodcastDiscoveryService {
    var response: HttpResponse<List<DiscoveredPodcast>> =
      HttpResponse.Success(
        listOf(DiscoveredPodcast("initial", "Initial podcast", null, null, null, null)),
        200,
      )
    var latency = Duration.ZERO
    var pending: CompletableDeferred<Unit>? = null
    var popularRequests = 0
    val queries = mutableListOf<String>()

    override suspend fun popular(): HttpResponse<List<DiscoveredPodcast>> {
      popularRequests++
      pending?.await()
      delay(latency)
      return response
    }

    override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
      queries += query
      return response
    }
  }
}
