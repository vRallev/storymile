package software.ralf.storymile.episodedetails

import software.ralf.storymile.rss.PodcastEpisode
import software.ralf.storymile.util.HtmlDescription

/** A feed episode and the show metadata needed to display it outside its source list. */
data class EpisodeDetails(
  /** Identifies the feed that owns the episode. */
  val feedUrl: String,
  /** Show name displayed with the episode. */
  val podcastTitle: String,
  /** Episode artwork, with feed artwork as a fallback. */
  val artworkUrl: String?,
  /** Metadata loaded from the feed. */
  val episode: PodcastEpisode,
) {
  /** Identifies an episode across feeds. */
  val key: String = "$feedUrl#${episode.id}"

  /** Plain text for both the list preview and full description. Empty when absent. */
  val description: String = HtmlDescription.plainText(episode.description).orEmpty()

  /** Duration in minutes and seconds, with hours when needed. Null when absent. */
  val duration: String? =
    episode.duration?.toComponents { hours, minutes, seconds, _ ->
      if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
      } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
      }
    }
}
