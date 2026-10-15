package software.ralf.storymile.discovery

import software.ralf.storymile.http.HttpResponse

/** Searches podcast titles and authors in the selected runtime mode. */
interface PodcastDiscoveryService {
  /**
   * Returns up to 25 podcasts from Apple's US catalog, or local samples in fake mode. Trims
   * [query]; a blank query returns an empty success without a request. HTTP, transport, and
   * decoding failures are returned; coroutine cancellation propagates.
   */
  suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>>
}
