package software.ralf.storymile.rss

/** An HTTP(S) media resource referenced by an RSS item. */
data class PodcastEnclosure(
  /** Absolute media URL. */
  val url: String,
  /** MIME type supplied by the feed, when available. */
  val type: String?,
  /** Non-negative content length in bytes, or null when absent or invalid. */
  val length: Long?,
)
