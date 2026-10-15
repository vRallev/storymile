package software.ralf.storymile.discovery

/** A catalog result. A missing feed URL means the catalog did not supply an RSS feed. */
data class DiscoveredPodcast(
  /** Stable identifier within the discovery catalog. */
  val id: String,
  /** Podcast title supplied by the catalog. */
  val title: String,
  /** Publisher or author, when available. */
  val author: String?,
  /** RSS feed URL, when available. */
  val feedUrl: String?,
  /** Cover image URL, when available. */
  val artworkUrl: String?,
  /** Catalog page URL, when available. */
  val catalogUrl: String?,
)
