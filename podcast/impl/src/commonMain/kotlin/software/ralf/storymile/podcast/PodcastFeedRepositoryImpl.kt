package software.ralf.storymile.podcast

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.podcast.PodcastFeedRepository.State

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PodcastFeedRepositoryImpl(
  private val service: PodcastFeedService,
  @ForScope(AppScope::class) private val coroutineScope: CoroutineScope,
) : PodcastFeedRepository {
  private val feeds = MutableStateFlow<Map<String, FeedEntry>>(emptyMap())

  override fun feed(feedUrl: String): StateFlow<State> = entry(feedUrl).state

  override fun refresh(feedUrl: String) {
    entry(feedUrl).refreshRequests.update { it + 1 }
  }

  private fun entry(feedUrl: String): FeedEntry {
    val url = feedUrl.trim()
    return feeds.updateAndGet { if (url in it) it else it + (url to FeedEntry(url)) }.getValue(url)
  }

  private suspend fun load(feedUrl: String): State {
    return try {
      when (val response = service.load(feedUrl)) {
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

  private inner class FeedEntry(feedUrl: String) {
    val refreshRequests = MutableStateFlow(0)

    // Map updates can race. Start sharing only on the entry retained in the map.
    val state: StateFlow<State> by lazy {
      refreshRequests
        .transformLatest {
          while (true) {
            emit(State.Loading)
            emit(load(feedUrl))
            delay(60.minutes)
          }
        }
        .stateIn(coroutineScope, SharingStarted.Lazily, State.Loading)
    }
  }
}
