package software.ralf.storymile.podcast

/** An HTTP(S) media resource for a podcast episode. */
data class PodcastEnclosure(
  /** Absolute media URL. */
  val url: String,
  /** MIME type supplied by the feed, when available. */
  val type: String?,
  /** Non-negative content length in bytes, or null when absent or invalid. */
  val length: Long?,
)
