package software.ralf.storymile.discovery

import software.ralf.storymile.http.HttpResponse

/** Finds podcasts in the selected runtime mode. */
interface PodcastDiscoveryService {
  /**
   * Returns up to 200 podcasts from Apple's US catalog, or local samples in fake mode. Trims
   * [query]; a blank query returns an empty success without a request. HTTP, transport, and
   * decoding failures are returned; coroutine cancellation propagates.
   */
  suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>>

  /**
   * Returns up to 200 podcasts from Apple's US Top Shows chart in rank order, or local samples in
   * fake mode. Chart results have no RSS feed URL. HTTP, transport, and decoding failures are
   * returned; coroutine cancellation propagates.
   */
  suspend fun popular(): HttpResponse<List<DiscoveredPodcast>>
}
