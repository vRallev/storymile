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

  /** Searches through [PodcastDiscoveryService.search] without caching results. */
  suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>>

  /**
   * Requests fresh recommendations and restarts the hourly interval. Replaces an active fetch.
   * Before the first collection of [popular], defers the request until collection starts.
   */
  fun refresh()

  /** The shared recommendation fetch state. */
  sealed interface State {
    /** The initial state or a fetch in progress. */
    data object Loading : State

    /** The last fetch failed. [refresh] can retry it before the next hourly refresh. */
    data object Error : State

    /** Recommendations in chart order, or local samples in fake mode. */
    data class Success(
      /** Podcasts returned by the latest successful fetch. */
      val podcasts: List<DiscoveredPodcast>,
    ) : State
  }
}
