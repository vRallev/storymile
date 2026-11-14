package software.ralf.storymile.show

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.episodedetails.EpisodeDetails

/**
 * Resolves catalog results to episode previews. Episode selection pushes details on the backstack.
 */
fun interface EpisodesPresenter :
  ComposePresenter<List<DiscoveredPodcast>, EpisodesPresenter.Model> {
  /** Creates an episode preview presenter for a catalog. */
  fun interface Factory {
    /** Limits the number of previews per feed. */
    fun create(episodesPerPodcast: Int): EpisodesPresenter
  }

  /** One preview and its navigation action. */
  data class Episode(
    /** Episode metadata and playback information. */
    val details: EpisodeDetails,
    /** Opens the episode details. */
    val onClick: () -> Unit,
  ) {
    /** Stable identity of the episode within its feed. */
    val key: String = details.key
  }

  /** Available previews may be displayed while other feeds load or fail. */
  data class Model(
    /** Previews in feed order. */
    val episodes: List<Episode>,
    /** True while a catalog lookup or feed load is in progress. */
    val loading: Boolean,
    /** True when at least one lookup or feed load failed. */
    val failed: Boolean,
    /** Retries failed lookups and feeds. */
    val onRetry: () -> Unit,
  ) : BaseModel
}
