package software.ralf.storymile.podcast

/** Show metadata and playable episodes from one feed response. */
data class PodcastFeed(
  /** Final feed URL after redirects. */
  val feedUrl: String,
  /** Show title supplied by the feed. */
  val title: String,
  /** Description, which can contain HTML. Treat it as untrusted content when rendering. */
  val description: String?,
  /** Publisher or author, when available. */
  val author: String?,
  /** Show artwork URL, when available. */
  val artworkUrl: String?,
  /** Show website URL, when available. */
  val link: String?,
  /** Episodes in feed order. Items without a title or HTTP(S) media enclosure are omitted. */
  val episodes: List<PodcastEpisode>,
)
