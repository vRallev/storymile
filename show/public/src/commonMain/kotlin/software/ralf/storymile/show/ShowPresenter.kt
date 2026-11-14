package software.ralf.storymile.show

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.podcast.PodcastFeed

/** Presents show details and an episode backstack. Push it onto the calling screen's backstack. */
interface ShowPresenter<ModelT : ShowPresenter.Model> : ComposePresenter<Unit, ModelT> {
  /** Identifies models that the caller can render as a dedicated detail entry. */
  interface Model : BaseModel

  /** Opens the same detail screen from a catalog result or a saved feed. */
  interface Factory {
    /** Opens [podcast] and lets wide screens switch between [catalog] results. */
    fun create(
      podcast: DiscoveredPodcast,
      catalog: List<DiscoveredPodcast>,
      source: ShowOrigin,
    ): ShowPresenter<*>

    /** Opens [feed] from local data and returns to the followed list on back. */
    fun create(feed: PodcastFeed): ShowPresenter<*>
  }
}
