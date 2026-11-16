package software.ralf.storymile.podcast

/** An episode and the show metadata needed to display it in the library. */
data class SavedEpisode(
  /** Feed URL that identifies the show. Together with the episode ID, it identifies the entry. */
  val feedUrl: String,
  /** Show title displayed with the episode. */
  val podcastTitle: String,
  /** Episode artwork with show artwork as a fallback. */
  val artworkUrl: String?,
  /** Episode metadata and its playable enclosure. */
  val episode: PodcastEpisode,
)
