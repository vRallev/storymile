package software.ralf.storymile.shows

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.episodedetails.EpisodeDetails

/** Episodes and their loading state for a show or discovery section. */
data class ShowEpisodes(
  /** Episodes in display order. */
  val episodes: List<Episode>,
  /** True while at least one feed is loading. */
  val loading: Boolean,
  /** True when a feed failed to load. */
  val failed: Boolean,
  /** Retries failed feeds. */
  val onRetry: () -> Unit,
) : BaseModel {
  /** One episode and its detail action. */
  data class Episode(
    /** Show and episode metadata. */
    val details: EpisodeDetails,
    /** Opens episode details. */
    val onClick: () -> Unit,
  ) {
    /** Stable identity across feeds. */
    val key: String = details.key
  }
}
