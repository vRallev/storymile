package software.ralf.storymile.podcastshows

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.podcast.PodcastFeed

/** Feed episodes and their loading state for a show or a set of recommendations. */
data class EpisodesModel(
  /** Episodes in display order. A partial list can be shown while another feed loads. */
  val episodes: List<Episode>,
  /** True while any requested episode list is loading. */
  val loading: Boolean,
  /** True when an episode list failed to load. */
  val failed: Boolean,
  /** Retries failed requests. */
  val onRetry: () -> Unit,
) : BaseModel {
  /** Episode metadata and its navigation action. */
  data class Episode(
    /** Metadata for the row and episode detail. */
    val details: EpisodeDetails,
    /** Opens the episode. */
    val onClick: () -> Unit,
  ) {
    /** Identifies the episode across feeds. */
    val key: String
      get() = details.key
  }
}

/** Builds rows in feed order. Pass the original subscription URL when the feed redirects. */
fun PodcastFeed.episodeRows(
  feedUrl: String = this.feedUrl,
  limit: Int = Int.MAX_VALUE,
  onSelect: (EpisodeDetails) -> Unit,
): List<EpisodesModel.Episode> =
  episodes.take(limit).map { episode ->
    val details =
      EpisodeDetails(
        feedUrl = feedUrl,
        podcastTitle = title,
        artworkUrl = episode.artworkUrl ?: artworkUrl,
        episode = episode,
      )
    EpisodesModel.Episode(details = details, onClick = { onSelect(details) })
  }
