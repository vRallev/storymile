package software.ralf.storymile.discovery

import software.ralf.storymile.http.HttpResponse

/** Finds podcasts and shares recommendations across discovery screens in the app session. */
interface PodcastDiscoveryRepository {
  /** Searches through [PodcastDiscoveryService.search] without caching results. */
  suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>>

  /**
   * Returns [PodcastDiscoveryService.popular] results from memory for one hour after a successful
   * fetch. Cache hits do not extend this period. Concurrent callers reuse a successful fetch. After
   * expiry, fetches again. Failures are returned without caching; cancellation propagates.
   */
  suspend fun popular(): HttpResponse<List<DiscoveredPodcast>>
}
