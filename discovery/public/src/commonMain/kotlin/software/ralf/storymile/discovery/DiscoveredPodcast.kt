package software.ralf.storymile.discovery

/**
 * A catalog result with a summary when available. [PodcastDiscoveryRepository.lookup] fills gaps.
 */
data class DiscoveredPodcast(
  /** Stable identifier within the discovery catalog. */
  val id: String,
  /** Podcast title supplied by the catalog. */
  val title: String,
  /** Publisher or author, when available. */
  val author: String?,
  /** Podcast feed URL, when available. */
  val feedUrl: String?,
  /** Cover image URL, when available. */
  val artworkUrl: String?,
  /** Catalog page URL, when available. */
  val catalogUrl: String?,
  /** Plain-text catalog summary or feed description. Null when missing or not yet loaded. */
  val description: String? = null,
)
