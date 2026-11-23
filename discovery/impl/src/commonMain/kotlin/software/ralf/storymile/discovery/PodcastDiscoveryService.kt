package software.ralf.storymile.discovery

import software.ralf.storymile.http.HttpResponse

/** Finds podcasts in the configured catalog. */
interface PodcastDiscoveryService {
  /**
   * Returns up to 200 podcasts. Trims [query]. A blank query returns an empty success without a
   * request. Returns HTTP, transport, and decoding failures. Coroutine cancellation propagates.
   */
  suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>>

  /**
   * Looks up one catalog ID, including its RSS URL when available. Returns an empty success for an
   * unknown ID. HTTP, transport, and decoding failures are returned; cancellation propagates.
   */
  suspend fun lookup(id: String): HttpResponse<List<DiscoveredPodcast>>

  /**
   * Returns up to 200 popular podcasts in catalog order. Apple chart results have no RSS feed URL.
   * Returns HTTP, transport, and decoding failures. Coroutine cancellation propagates.
   */
  suspend fun popular(): HttpResponse<List<DiscoveredPodcast>>
}
