package software.ralf.storymile.discovery

import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.http.HttpResponse

/** Finds podcasts and shares recommendations across discovery screens in the app session. */
interface PodcastDiscoveryRepository {
  /**
   * Loads recommendations on first collection, then refreshes one hour after each completed fetch.
   * Shares the latest state across collectors. Refreshes continue until the app session ends, even
   * without collectors.
   */
  val popular: StateFlow<State>

  /**
   * Shares metadata by ID, with cached chart, search, or feed data available immediately. On first
   * collection, resolves a missing feed URL and fills a missing description from the feed. Retains
   * metadata during loading or failures. Uses the hourly refresh policy of [popular]. Success
   * contains zero or one podcast; a null description means missing or not yet loaded.
   */
  fun lookup(id: String): StateFlow<State>

  /**
   * Refreshes a podcast's catalog lookup and known feed, restarting their hourly intervals. Defers
   * work until the first collection of [lookup].
   */
  fun refresh(id: String)

  /**
   * Returns up to 200 podcasts from Apple's US catalog, or local samples in fake mode, without
   * caching search requests. Retains returned metadata for [lookup]. Trims [query]; a blank query
   * returns an empty success without a request. Returns HTTP, transport, and decoding failures;
   * coroutine cancellation propagates.
   */
  suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>>

  /**
   * Requests fresh recommendations and restarts the hourly interval. Replaces an active fetch.
   * Before the first collection of [popular], defers the request until collection starts.
   */
  fun refresh()

  /** The shared recommendations or podcast metadata state. */
  sealed interface State {
    /** The initial state or a fetch in progress. */
    data object Loading : State

    /** The last fetch failed. [refresh] can retry it before the next hourly refresh. */
    data object Error : State

    /** Chart recommendations, a catalog lookup result, or local samples in fake mode. */
    data class Success(
      /** Available podcasts, including cached descriptions when supplied by [lookup]. */
      val podcasts: List<DiscoveredPodcast>,
    ) : State
  }
}
