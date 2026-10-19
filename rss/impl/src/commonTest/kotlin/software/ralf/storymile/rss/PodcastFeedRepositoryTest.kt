package software.ralf.storymile.rss

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.rss.PodcastFeedRepository.State

class PodcastFeedRepositoryTest {
  @Test
  fun `collectors share one lazy fetch per trimmed URL`() = runTest {
    val service = TestService()
    val repository: PodcastFeedRepository = PodcastFeedRepositoryImpl(service, backgroundScope)
    val feed = repository.feed(" https://feeds.test/one ")
    repository.refresh("https://feeds.test/one")
    advanceTimeBy(120.minutes)
    runCurrent()
    assertThat(service.requests).isEmpty()

    feed.test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      repository.feed("https://feeds.test/one").test {
        assertThat(awaitItem()).isEqualTo(State.Loading)
        assertThat(awaitItem()).isEqualTo(State.Success(service.feed("https://feeds.test/one")))
      }
      assertThat(awaitItem()).isEqualTo(State.Success(service.feed("https://feeds.test/one")))
    }
    repository.feed("https://feeds.test/one").test {
      assertThat(awaitItem()).isEqualTo(State.Success(service.feed("https://feeds.test/one")))
      runCurrent()
      expectNoEvents()
    }
    assertThat(service.requests).containsExactly("https://feeds.test/one")
  }

  @Test
  fun `each feed refreshes 60 minutes after completion including without collectors`() = runTest {
    val service = TestService()
    val repository: PodcastFeedRepository = PodcastFeedRepositoryImpl(service, backgroundScope)
    val feed = repository.feed("one")
    feed.test {
      awaitItem()
      assertThat(awaitItem()).isEqualTo(State.Success(service.feed("one")))
    }
    service.title = "Updated"
    advanceTimeBy(59.minutes)
    runCurrent()
    assertThat(service.requests).containsExactly("one")
    advanceTimeBy(1.minutes)
    runCurrent()
    assertThat(feed.value).isEqualTo(State.Loading)
    advanceTimeBy(1.minutes)
    runCurrent()
    assertThat(feed.value).isEqualTo(State.Success(service.feed("one")))
    assertThat(service.requests).containsExactly("one", "one")
  }

  @Test
  fun `feeds stay independent and retry replaces errors without refreshing other feeds`() =
    runTest {
      val service = TestService()
      val repository: PodcastFeedRepository = PodcastFeedRepositoryImpl(service, backgroundScope)
      repository.feed("one").test {
        awaitItem()
        awaitItem()
        service.error = HttpResponse.Error.Server(503)
        repository.feed("two").test {
          awaitItem()
          assertThat(awaitItem()).isEqualTo(State.Error)
          service.error = null
          repository.refresh("two")
          assertThat(awaitItem()).isEqualTo(State.Loading)
          assertThat(awaitItem()).isEqualTo(State.Success(service.feed("two")))
        }
        expectNoEvents()
      }
      assertThat(service.requests).containsExactly("one", "two", "two")
    }

  @Test
  fun `manual refresh replaces an active fetch and restarts the interval`() = runTest {
    val service = TestService()
    val repository: PodcastFeedRepository = PodcastFeedRepositoryImpl(service, backgroundScope)
    service.pending = CompletableDeferred()
    repository.feed("one").test {
      awaitItem()
      runCurrent()
      assertThat(service.requests).containsExactly("one")
      service.pending = null
      repository.refresh("one")
      assertThat(awaitItem()).isEqualTo(State.Success(service.feed("one")))
      assertThat(service.cancelled).isEqualTo(1)
      advanceTimeBy(30.minutes)
      repository.refresh("one")
      assertThat(awaitItem()).isEqualTo(State.Loading)
      awaitItem()
      advanceTimeBy(59.minutes)
      runCurrent()
      expectNoEvents()
      assertThat(service.requests).containsExactly("one", "one", "one")
      advanceTimeBy(1.minutes)
      assertThat(awaitItem()).isEqualTo(State.Loading)
      awaitItem()
      assertThat(service.requests).containsExactly("one", "one", "one", "one")
    }
  }

  @Test
  fun `exceptions become retryable errors`() = runTest {
    val service = TestService()
    service.failure = IllegalArgumentException("Invalid feed")
    val repository: PodcastFeedRepository = PodcastFeedRepositoryImpl(service, backgroundScope)
    repository.feed("one").test {
      awaitItem()
      assertThat(awaitItem()).isEqualTo(State.Error)
      service.failure = null
      repository.refresh("one")
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(service.feed("one")))
    }
  }

  @Test
  fun `scope cancellation stops requests and refreshes without publishing an error`() = runTest {
    val service = TestService()
    service.pending = CompletableDeferred()
    val scope = CoroutineScope(backgroundScope.coroutineContext + Job())
    val repository: PodcastFeedRepository = PodcastFeedRepositoryImpl(service, scope)
    repository.feed("one").test {
      awaitItem()
      runCurrent()
      scope.cancel()
      runCurrent()
      assertThat(service.cancelled).isEqualTo(1)
      repository.refresh("one")
      advanceTimeBy(120.minutes)
      runCurrent()
      expectNoEvents()
      assertThat(service.requests).containsExactly("one")
    }
  }

  private class TestService : PodcastFeedService {
    var title = "Original"
    var error: HttpResponse.Error? = null
    var failure: Exception? = null
    var pending: CompletableDeferred<PodcastFeed>? = null
    val requests = mutableListOf<String>()
    var cancelled = 0

    override suspend fun load(feedUrl: String): HttpResponse<PodcastFeed> {
      requests += feedUrl
      return try {
        val blocked = pending
        delay(1.minutes)
        failure?.let { throw it }
        error ?: HttpResponse.Success(blocked?.await() ?: feed(feedUrl), 200)
      } catch (exception: CancellationException) {
        cancelled++
        throw exception
      }
    }

    fun feed(url: String) = PodcastFeed(url, title, null, null, null, null, emptyList())
  }
}
