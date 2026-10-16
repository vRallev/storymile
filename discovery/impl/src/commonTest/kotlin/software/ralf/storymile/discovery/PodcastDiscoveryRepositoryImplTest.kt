package software.ralf.storymile.discovery

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.http.HttpResponse

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastDiscoveryRepositoryImplTest {
  @Test
  fun `first collection starts one shared fetch and later collectors reuse its state`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, backgroundScope)

    assertThat(repository.popular.value).isEqualTo(State.Loading)
    repository.refresh()
    advanceTimeBy(2.hours)
    runCurrent()
    assertThat(service.popularRequests).isEqualTo(0)

    repository.popular.test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      repository.popular.test {
        assertThat(awaitItem()).isEqualTo(State.Loading)
        assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      }
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      assertThat(service.popularRequests).isEqualTo(1)
    }
    repository.popular.test {
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      runCurrent()
      expectNoEvents()
      assertThat(service.popularRequests).isEqualTo(1)
    }
  }

  @Test
  fun `hourly refresh starts one hour after completion and supports empty results`() = runTest {
    val service = TestService()
    service.podcasts = emptyList()
    service.latency = 1.minutes
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, backgroundScope)

    repository.popular.test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(emptyList()))
      service.podcasts = listOf(podcast("updated"))

      advanceTimeBy(1.hours - 1.milliseconds)
      runCurrent()
      expectNoEvents()
      assertThat(service.popularRequests).isEqualTo(1)
      advanceTimeBy(1.milliseconds)
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      assertThat(service.popularRequests).isEqualTo(2)
    }
  }

  @Test
  fun `hourly refresh continues after the last collector leaves`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, backgroundScope)
    repository.popular.test {
      awaitItem()
      awaitItem()
    }

    service.podcasts = listOf(podcast("updated"))
    advanceTimeBy(1.hours)
    runCurrent()
    assertThat(repository.popular.value).isEqualTo(State.Success(service.podcasts))
    assertThat(service.popularRequests).isEqualTo(2)
    repository.popular.test {
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      assertThat(service.popularRequests).isEqualTo(2)
    }
  }

  @Test
  fun `manual refresh replaces cached data and restarts the hourly interval`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, backgroundScope)
    repository.popular.test {
      awaitItem()
      awaitItem()
      advanceTimeBy(30.minutes)
      service.podcasts = listOf(podcast("manual"))
      repository.refresh()
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      assertThat(service.popularRequests).isEqualTo(2)

      advanceTimeBy(30.minutes)
      runCurrent()
      expectNoEvents()
      assertThat(service.popularRequests).isEqualTo(2)
      advanceTimeBy(30.minutes)
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      assertThat(service.popularRequests).isEqualTo(3)
    }
  }

  @Test
  fun `failed fetches can be retried manually and unexpected errors keep refresh usable`() =
    runTest {
      val service = TestService()
      service.failure = HttpResponse.Error.Server(503)
      val repository: PodcastDiscoveryRepository =
        PodcastDiscoveryRepositoryImpl(service, backgroundScope)
      repository.popular.test {
        assertThat(awaitItem()).isEqualTo(State.Loading)
        assertThat(awaitItem()).isEqualTo(State.Error)

        service.exception = IllegalStateException("Unavailable catalog")
        repository.refresh()
        assertThat(awaitItem()).isEqualTo(State.Loading)
        assertThat(awaitItem()).isEqualTo(State.Error)

        service.exception = null
        service.failure = null
        repository.refresh()
        assertThat(awaitItem()).isEqualTo(State.Loading)
        assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
        assertThat(service.popularRequests).isEqualTo(3)
      }
    }

  @Test
  fun `hourly refresh recovers after a failed fetch`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, backgroundScope)
    repository.popular.test {
      awaitItem()
      awaitItem()
      service.failure = HttpResponse.Error.Server(503)
      advanceTimeBy(1.hours)
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Error)

      service.failure = null
      service.podcasts = listOf(podcast("recovered"))
      advanceTimeBy(1.hours)
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      assertThat(service.popularRequests).isEqualTo(3)
    }
  }

  @Test
  fun `manual refresh cancels an active fetch and publishes only its replacement`() = runTest {
    val service = TestService()
    val pending = CompletableDeferred<Unit>()
    service.pending = pending
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, backgroundScope)
    repository.popular.test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      runCurrent()
      assertThat(service.popularRequests).isEqualTo(1)

      service.pending = null
      service.podcasts = listOf(podcast("replacement"))
      repository.refresh()
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
      assertThat(service.cancelledRequests).isEqualTo(1)
      assertThat(service.popularRequests).isEqualTo(2)
      pending.complete(Unit)
      runCurrent()
      expectNoEvents()
    }
  }

  @Test
  fun `ending the app coroutine scope cancels a fetch and stops future refreshes`() = runTest {
    val service = TestService()
    service.pending = CompletableDeferred()
    val appScope =
      CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
    val repository: PodcastDiscoveryRepository = PodcastDiscoveryRepositoryImpl(service, appScope)
    repository.popular.test {
      awaitItem()
      runCurrent()
      assertThat(service.popularRequests).isEqualTo(1)
      appScope.cancel()
      runCurrent()
      assertThat(service.cancelledRequests).isEqualTo(1)
      repository.refresh()
      advanceTimeBy(2.hours)
      runCurrent()
      expectNoEvents()
      assertThat(service.popularRequests).isEqualTo(1)
    }
  }

  @Test
  fun `searches stay uncached without starting recommendations`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(service, backgroundScope)
    assertThat(repository.search("science")).isEqualTo(HttpResponse.Success(service.podcasts, 200))
    service.podcasts = listOf(podcast("second-search"))
    assertThat(repository.search("science")).isEqualTo(HttpResponse.Success(service.podcasts, 200))
    assertThat(service.queries).containsExactly("science", "science")
    runCurrent()
    assertThat(repository.popular.value).isEqualTo(State.Loading)
    assertThat(service.popularRequests).isEqualTo(0)
  }

  private fun podcast(id: String) = DiscoveredPodcast(id, "Podcast $id", null, null, null, null)

  private class TestService : PodcastDiscoveryService {
    var podcasts = listOf(DiscoveredPodcast("initial", "Initial podcast", null, null, null, null))
    var failure: HttpResponse.Error? = null
    var exception: Exception? = null
    var latency = Duration.ZERO
    var pending: CompletableDeferred<Unit>? = null
    var popularRequests = 0
    var cancelledRequests = 0
    val queries = mutableListOf<String>()

    override suspend fun popular(): HttpResponse<List<DiscoveredPodcast>> {
      popularRequests++
      val result = failure ?: HttpResponse.Success(podcasts, 200)
      try {
        pending?.await()
        delay(latency)
        exception?.let { throw it }
        return result
      } catch (exception: CancellationException) {
        cancelledRequests++
        throw exception
      }
    }

    override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
      queries += query
      return HttpResponse.Success(podcasts, 200)
    }
  }
}
