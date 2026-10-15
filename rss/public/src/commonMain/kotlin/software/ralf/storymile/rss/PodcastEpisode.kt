package software.ralf.storymile.rss

import kotlin.time.Duration

/** An RSS item with a playable media enclosure. */
data class PodcastEpisode(
  /** Feed GUID, or the enclosure URL when no GUID is supplied. Unique within a feed. */
  val id: String,
  /** Episode title supplied by the feed. */
  val title: String,
  /** Description, which can contain HTML. Treat it as untrusted content when rendering. */
  val description: String?,
  /** Episode author, with the show author as a fallback. */
  val author: String?,
  /** Episode artwork URL, with show artwork as a fallback. */
  val artworkUrl: String?,
  /** Original RSS publication date, without timezone conversion or format normalization. */
  val publishedAt: String?,
  /** Declared playback duration, or null when absent or invalid. */
  val duration: Duration?,
  /** Media location and optional server metadata. */
  val enclosure: PodcastEnclosure,
)
