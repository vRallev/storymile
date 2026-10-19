package software.ralf.storymile.discovery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.podcast.PodcastFeedRepository.State as FeedState
import software.ralf.storymile.util.HtmlDescription

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PodcastDiscoveryRepositoryImpl(
  private val service: PodcastDiscoveryService,
  private val feeds: PodcastFeedRepository,
  @ForScope(AppScope::class) private val coroutineScope: CoroutineScope,
) : PodcastDiscoveryRepository {
  private val recommendations = Request { service.popular() }
  private val lookups = MutableStateFlow<Map<String, LookupEntry>>(emptyMap())

  override val popular: StateFlow<State> = recommendations.state

  override fun lookup(id: String): StateFlow<State> = lookupEntry(id).observe()

  override fun refresh(id: String) {
    lookupEntry(id).refreshRequests.update { it + 1 }
  }

  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
    val response = service.search(query)
    if (response is HttpResponse.Success) {
      rememberPodcasts(response.data)
    }
    return response
  }

  override fun refresh() {
    recommendations.refreshRequests.update { it + 1 }
  }

  private fun lookupEntry(id: String): LookupEntry =
    lookups.updateAndGet { if (id in it) it else it + (id to LookupEntry(id)) }.getValue(id)

  private fun rememberPodcasts(podcasts: List<DiscoveredPodcast>) {
    podcasts.forEach { lookupEntry(it.id).remember(it) }
  }

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

    val state: StateFlow<State> by lazy {
      refreshRequests
        .transformLatest {
          while (true) {
            emit(State.Loading)
            val result = load(request)
            if (result is State.Success) {
              rememberPodcasts(result.podcasts)
            }
            emit(result)
            delay(1.hours)
          }
        }
        .stateIn(coroutineScope, SharingStarted.Lazily, State.Loading)
    }
  }

  private inner class LookupEntry(private val id: String) {
    val refreshRequests = MutableStateFlow(0)

    private val state = MutableStateFlow<State>(State.Loading)

    // Keep catalog summaries distinct from RSS descriptions so RSS updates stay observed.
    private val metadata = MutableStateFlow<DiscoveredPodcast?>(null)
    private val observation = lazy {
      coroutineScope.launch {
        state.subscriptionCount.first { it > 0 }
        launch { observeDescription() }
        refreshRequests.collectLatest { refresh ->
          if (refresh > 0) {
            metadata.value?.feedUrl?.let { feeds.refresh(it) }
          }
          if (refresh > 0 || metadata.value?.feedUrl == null) {
            loadMetadata()
          }
          while (true) {
            delay(1.hours)
            loadMetadata()
          }
        }
      }
    }

    fun observe(): StateFlow<State> {
      publishMetadata(includeCachedFeed = true)
      observation.value
      return state
    }

    fun remember(podcast: DiscoveredPodcast) {
      metadata.update { previous ->
        podcast.copy(
          feedUrl = podcast.feedUrl?.trim()?.takeIf { it.isNotEmpty() } ?: previous?.feedUrl,
          description = podcast.description?.takeIf { it.isNotBlank() } ?: previous?.description,
        )
      }
      publishMetadata(includeCachedFeed = observation.isInitialized())
    }

    private fun publishMetadata(includeCachedFeed: Boolean) {
      val podcast = metadata.value ?: return
      val feedUrl = podcast.feedUrl
      val feed =
        if (includeCachedFeed && podcast.description == null && feedUrl != null) {
          feeds.feed(feedUrl)
        } else {
          null
        }
      state.update { previous ->
        if (metadata.value != podcast) {
          previous
        } else {
          val cachedFeed = feed?.value as? FeedState.Success
          val description =
            if (cachedFeed != null) {
              HtmlDescription.plainText(cachedFeed.feed.description)
            } else {
              podcast.description
                ?: (previous as? State.Success)
                  ?.podcasts
                  ?.firstOrNull { it.feedUrl == podcast.feedUrl }
                  ?.description
            }
          State.Success(listOf(podcast.copy(description = description)))
        }
      }
    }

    private suspend fun loadMetadata() {
      state.update { if (it is State.Success) it else State.Loading }
      val result = load { service.lookup(id) }
      val podcast = (result as? State.Success)?.podcasts?.firstOrNull { it.id == id }
      if (podcast != null) {
        remember(podcast)
      } else {
        state.update { if (it is State.Success) it else result }
      }
    }

    private suspend fun observeDescription() {
      metadata.collectLatest { podcast ->
        val feedUrl = podcast?.feedUrl
        if (podcast != null && podcast.description == null && feedUrl != null) {
          feeds.feed(feedUrl).filterIsInstance<FeedState.Success>().collect { result ->
            val description = HtmlDescription.plainText(result.feed.description)
            state.update { previous ->
              if (metadata.value == podcast) {
                State.Success(listOf(podcast.copy(description = description)))
              } else {
                previous
              }
            }
          }
        }
      }
    }
  }
}
