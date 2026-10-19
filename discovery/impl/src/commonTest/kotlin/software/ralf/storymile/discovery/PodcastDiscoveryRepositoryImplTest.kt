package software.ralf.storymile.discovery

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
import software.ralf.storymile.discovery.presentation.TestPodcastFeeds
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.rss.PodcastFeed
import software.ralf.storymile.rss.PodcastFeedRepository.State as FeedState

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastDiscoveryRepositoryImplTest {
  @Test
  fun `first collection starts one shared fetch and later collectors reuse its state`() = runTest {
    val service = TestService()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = TestPodcastFeeds(),
        coroutineScope = backgroundScope,
      )

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
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = TestPodcastFeeds(),
        coroutineScope = backgroundScope,
      )

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
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = TestPodcastFeeds(),
        coroutineScope = backgroundScope,
      )
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
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = TestPodcastFeeds(),
        coroutineScope = backgroundScope,
      )
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
        PodcastDiscoveryRepositoryImpl(
          service = service,
          feeds = TestPodcastFeeds(),
          coroutineScope = backgroundScope,
        )
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
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = TestPodcastFeeds(),
        coroutineScope = backgroundScope,
      )
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
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = TestPodcastFeeds(),
        coroutineScope = backgroundScope,
      )
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
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = TestPodcastFeeds(),
        coroutineScope = appScope,
      )
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
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = TestPodcastFeeds(),
        coroutineScope = backgroundScope,
      )
    assertThat(repository.search("science")).isEqualTo(HttpResponse.Success(service.podcasts, 200))
    service.podcasts = listOf(podcast("second-search"))
    assertThat(repository.search("science")).isEqualTo(HttpResponse.Success(service.podcasts, 200))
    assertThat(service.queries).containsExactly("science", "science")
    runCurrent()
    assertThat(repository.popular.value).isEqualTo(State.Loading)
    assertThat(service.popularRequests).isEqualTo(0)
  }

  @Test
  fun `catalog lookups share hourly snapshots and preserve metadata when refresh fails`() =
    runTest {
      val service = TestService()
      service.latency = 1.milliseconds
      val repository: PodcastDiscoveryRepository =
        PodcastDiscoveryRepositoryImpl(
          service = service,
          feeds = TestPodcastFeeds(),
          coroutineScope = backgroundScope,
        )
      val lookup = repository.lookup("initial")
      runCurrent()
      assertThat(service.lookups).isEmpty()
      lookup.test {
        assertThat(awaitItem()).isEqualTo(State.Loading)
        assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
        repository.lookup("initial").test {
          assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
          expectNoEvents()
        }
        assertThat(service.lookups).containsExactly("initial")
        advanceTimeBy(1.hours - 1.milliseconds)
        runCurrent()
        expectNoEvents()
        service.failure = HttpResponse.Error.Server(503)
        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertThat(lookup.value).isEqualTo(State.Success(service.podcasts))
        expectNoEvents()
        service.failure = null
        service.podcasts = listOf(podcast("initial").copy(title = "Updated title"))
        repository.refresh("initial")
        assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
        assertThat(service.lookups).containsExactly("initial", "initial", "initial")
        assertThat(service.popularRequests).isEqualTo(0)
      }
    }

  @Test
  fun `catalog results seed uncollected lookups without starting catalog or RSS requests`() =
    runTest {
      val service = TestService()
      val chartPodcast = podcast("chart").copy(description = "Chart summary")
      service.podcasts = listOf(chartPodcast)
      val feeds = TestPodcastFeeds()
      val repository: PodcastDiscoveryRepository =
        PodcastDiscoveryRepositoryImpl(
          service = service,
          feeds = feeds,
          coroutineScope = backgroundScope,
        )
      val chartLookup = repository.lookup(chartPodcast.id)
      assertThat(chartLookup.value).isEqualTo(State.Loading)
      repository.popular.test {
        awaitItem()
        assertThat(awaitItem()).isEqualTo(State.Success(listOf(chartPodcast)))
      }
      assertThat(chartLookup.value).isEqualTo(State.Success(listOf(chartPodcast)))

      val searchPodcast = podcast("search").copy(feedUrl = "search-feed")
      val searchLookup = repository.lookup(searchPodcast.id)
      service.podcasts = listOf(searchPodcast)
      repository.search("science")
      assertThat(searchLookup.value).isEqualTo(State.Success(listOf(searchPodcast)))
      assertThat(repository.lookup(searchPodcast.id).value)
        .isEqualTo(State.Success(listOf(searchPodcast)))
      runCurrent()
      assertThat(service.lookups).isEmpty()
      assertThat(feeds.isObserved("search-feed")).isFalse()
    }

  @Test
  fun `known feed and description need no initial catalog or RSS fetch`() = runTest {
    val service = TestService()
    val podcast = podcast("initial").copy(feedUrl = "feed", description = "A catalog summary")
    service.podcasts = listOf(podcast)
    val feeds = TestPodcastFeeds()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = feeds,
        coroutineScope = backgroundScope,
      )
    repository.search("science")
    val lookup = repository.lookup(podcast.id)
    assertThat(lookup.value).isEqualTo(State.Success(listOf(podcast)))
    lookup.test {
      assertThat(awaitItem()).isEqualTo(State.Success(listOf(podcast)))
      runCurrent()
      expectNoEvents()
    }
    assertThat(service.lookups).isEmpty()
    assertThat(feeds.isObserved("feed")).isFalse()
  }

  @Test
  fun `refreshing an unopened lookup defers catalog and RSS work until collection`() = runTest {
    val service = TestService()
    val podcast = podcast("initial").copy(feedUrl = "feed", description = "Catalog summary")
    service.podcasts = listOf(podcast)
    val feeds = TestPodcastFeeds()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = feeds,
        coroutineScope = backgroundScope,
      )
    repository.search("science")
    val lookup = repository.lookup(podcast.id)
    repository.refresh(podcast.id)
    advanceTimeBy(2.hours)
    runCurrent()
    assertThat(lookup.value).isEqualTo(State.Success(listOf(podcast)))
    assertThat(service.lookups).isEmpty()
    assertThat(feeds.refreshed).isEmpty()
    lookup.test {
      assertThat(awaitItem()).isEqualTo(State.Success(listOf(podcast)))
      runCurrent()
      expectNoEvents()
      assertThat(service.lookups).containsExactly(podcast.id)
      assertThat(feeds.refreshed).containsExactly("feed")
    }
  }

  @Test
  fun `chart summary stays available while collection resolves the missing feed URL`() = runTest {
    val service = TestService()
    val chartPodcast = podcast("initial").copy(description = "Chart summary")
    service.podcasts = listOf(chartPodcast)
    val feeds = TestPodcastFeeds()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = feeds,
        coroutineScope = backgroundScope,
      )
    repository.popular.test {
      awaitItem()
      awaitItem()
    }
    service.latency = 1.milliseconds
    service.podcasts = listOf(chartPodcast.copy(feedUrl = "feed", description = null))
    repository.lookup(chartPodcast.id).test {
      assertThat(awaitItem()).isEqualTo(State.Success(listOf(chartPodcast)))
      assertThat(awaitItem()).isEqualTo(State.Success(listOf(chartPodcast.copy(feedUrl = "feed"))))
      assertThat(service.lookups).containsExactly(chartPodcast.id)
      assertThat(feeds.isObserved("feed")).isFalse()
    }
  }

  @Test
  fun `lookup emits the resolved feed before its description finishes loading`() = runTest {
    val service = TestService()
    val podcast = podcast("initial").copy(feedUrl = "feed")
    service.podcasts = listOf(podcast)
    service.latency = 1.milliseconds
    val feeds = TestPodcastFeeds()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = feeds,
        coroutineScope = backgroundScope,
      )
    repository.lookup(podcast.id).test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(listOf(podcast)))
      runCurrent()
      assertThat(feeds.isObserved("feed")).isTrue()
      feeds.update(
        url = "feed",
        value =
          FeedState.Success(feed(url = "feed", description = "<p>Stories &amp; science.</p>")),
      )
      assertThat(awaitItem())
        .isEqualTo(State.Success(listOf(podcast.copy(description = "Stories & science."))))
    }
  }

  @Test
  fun `known feed enriches metadata and preserves its description during refresh failures`() =
    runTest {
      val service = TestService()
      val podcast = podcast("initial").copy(feedUrl = "  feed  ")
      service.podcasts = listOf(podcast)
      val normalized = podcast.copy(feedUrl = "feed")
      val feeds = TestPodcastFeeds()
      val repository: PodcastDiscoveryRepository =
        PodcastDiscoveryRepositoryImpl(
          service = service,
          feeds = feeds,
          coroutineScope = backgroundScope,
        )
      repository.search("science")
      val lookup = repository.lookup(podcast.id)
      assertThat(lookup.value).isEqualTo(State.Success(listOf(normalized)))
      lookup.test {
        assertThat(awaitItem()).isEqualTo(State.Success(listOf(normalized)))
        runCurrent()
        assertThat(service.lookups).isEmpty()
        assertThat(feeds.isObserved("feed")).isTrue()
        feeds.update("feed", FeedState.Success(feed(url = "feed", description = "Loaded")))
        val loaded = normalized.copy(description = "Loaded")
        assertThat(awaitItem()).isEqualTo(State.Success(listOf(loaded)))

        service.failure = HttpResponse.Error.Server(503)
        repository.refresh(podcast.id)
        runCurrent()
        assertThat(service.lookups).containsExactly(podcast.id)
        assertThat(feeds.refreshed).containsExactly("feed")
        assertThat(lookup.value).isEqualTo(State.Success(listOf(loaded)))
        expectNoEvents()
        feeds.update("feed", FeedState.Error)
        runCurrent()
        assertThat(lookup.value).isEqualTo(State.Success(listOf(loaded)))
        expectNoEvents()

        feeds.update("feed", FeedState.Success(feed(url = "feed", description = "Updated")))
        val updated = normalized.copy(description = "Updated")
        assertThat(awaitItem()).isEqualTo(State.Success(listOf(updated)))
        assertThat(repository.lookup(podcast.id).value).isEqualTo(State.Success(listOf(updated)))
        repository.lookup(podcast.id).test {
          assertThat(awaitItem()).isEqualTo(State.Success(listOf(updated)))
        }
      }
      assertThat(feeds.requested.distinct()).containsExactly("feed")
    }

  @Test
  fun `refresh by ID retries failed catalog and RSS loads`() = runTest {
    val service = TestService()
    service.latency = 1.milliseconds
    service.failure = HttpResponse.Error.Server(503)
    val feeds = TestPodcastFeeds()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = feeds,
        coroutineScope = backgroundScope,
      )
    val initial = podcast("initial")
    repository.lookup(initial.id).test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Error)
      assertThat(feeds.requested).isEmpty()
      service.failure = null
      val resolved = initial.copy(feedUrl = "feed")
      service.podcasts = listOf(resolved)
      feeds.update("feed", FeedState.Error)
      repository.refresh(initial.id)
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(listOf(resolved)))
      assertThat(service.lookups).containsExactly("initial", "initial")

      repository.refresh(initial.id)
      advanceTimeBy(2.milliseconds)
      runCurrent()
      assertThat(feeds.refreshed).containsExactly("feed")
      expectNoEvents()
      feeds.update("feed", FeedState.Success(feed(url = "feed", description = "Recovered")))
      assertThat(awaitItem())
        .isEqualTo(State.Success(listOf(resolved.copy(description = "Recovered"))))
      assertThat(service.lookups).containsExactly("initial", "initial", "initial")
    }
  }

  @Test
  fun `missing lookup results and feed URLs do not start RSS loads`() = runTest {
    val service = TestService()
    val feeds = TestPodcastFeeds()
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = feeds,
        coroutineScope = backgroundScope,
      )
    repository.lookup("missing").test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(emptyList()))
    }
    repository.lookup("initial").test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(service.podcasts))
    }
    assertThat(feeds.requested).isEmpty()
  }

  @Test
  fun `cached feed descriptions are available before collection including missing content`() =
    runTest {
      val service = TestService()
      val feeds = TestPodcastFeeds()
      val repository: PodcastDiscoveryRepository =
        PodcastDiscoveryRepositoryImpl(
          service = service,
          feeds = feeds,
          coroutineScope = backgroundScope,
        )
      listOf("<p>Cached &amp; ready</p>" to "Cached & ready", "<p>&nbsp;</p>" to null)
        .forEachIndexed { index, (raw, expected) ->
          val podcast = podcast("$index").copy(feedUrl = "feed-$index")
          service.podcasts = listOf(podcast)
          feeds.update(
            url = "feed-$index",
            value = FeedState.Success(feed(url = "feed-$index", description = raw)),
          )
          repository.search("science")
          val enriched = podcast.copy(description = expected)
          val lookup = repository.lookup(podcast.id)
          assertThat(lookup.value).isEqualTo(State.Success(listOf(enriched)))
          assertThat(feeds.isObserved("feed-$index")).isFalse()
          lookup.test {
            assertThat(awaitItem()).isEqualTo(State.Success(listOf(enriched)))
            runCurrent()
            expectNoEvents()
            assertThat(feeds.isObserved("feed-$index")).isTrue()
          }
        }
      assertThat(service.lookups).isEmpty()
    }

  @Test
  fun `shared lookups follow hourly feed URL changes and stop observing the old feed`() = runTest {
    val service = TestService()
    val initial = podcast("initial")
    service.podcasts = listOf(initial.copy(feedUrl = "first"))
    service.latency = 1.milliseconds
    val feeds = TestPodcastFeeds()
    feeds.update("first", FeedState.Success(feed(url = "first", description = "First")))
    val repository: PodcastDiscoveryRepository =
      PodcastDiscoveryRepositoryImpl(
        service = service,
        feeds = feeds,
        coroutineScope = backgroundScope,
      )
    val lookup = repository.lookup(initial.id)
    lookup.test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      advanceTimeBy(2.milliseconds)
      runCurrent()
      val first = initial.copy(feedUrl = "first", description = "First")
      assertThat(lookup.value).isEqualTo(State.Success(listOf(first)))
      assertThat(service.lookups).containsExactly("initial")

      service.podcasts = listOf(initial.copy(feedUrl = "second"))
      feeds.update("second", FeedState.Success(feed(url = "second", description = "Second")))
      advanceTimeBy(1.hours)
      runCurrent()
      val second = initial.copy(feedUrl = "second", description = "Second")
      assertThat(lookup.value).isEqualTo(State.Success(listOf(second)))
      assertThat(feeds.isObserved("first")).isFalse()
      assertThat(feeds.isObserved("second")).isTrue()
      feeds.update("first", FeedState.Success(feed(url = "first", description = "Old update")))
      runCurrent()
      assertThat(lookup.value).isEqualTo(State.Success(listOf(second)))
      assertThat(service.lookups).containsExactly("initial", "initial")
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun feed(url: String, description: String?) =
    PodcastFeed(
      feedUrl = url,
      title = "Show",
      description = description,
      author = null,
      artworkUrl = null,
      link = null,
      episodes = emptyList(),
    )

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
    val lookups = mutableListOf<String>()

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

    override suspend fun lookup(id: String): HttpResponse<List<DiscoveredPodcast>> {
      lookups += id
      delay(latency)
      return failure ?: HttpResponse.Success(podcasts.filter { it.id == id }, 200)
    }

    override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
      queries += query
      return HttpResponse.Success(podcasts, 200)
    }
  }
}
