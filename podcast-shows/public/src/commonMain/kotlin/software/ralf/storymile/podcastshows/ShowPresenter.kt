package software.ralf.storymile.podcastshows

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.podcast.PodcastFeed

/** Presents a selected show and its episode details in the caller's backstack. */
interface ShowPresenter : ComposePresenter<Unit, ShowPresenter.Model> {
  /** Opens shows from either a discovery catalog or the user's saved feeds. */
  interface Factory {
    /** Opens [podcast] with [catalog] as the list displayed beside it in wide layouts. */
    fun create(
      podcast: DiscoveredPodcast,
      catalog: List<DiscoveredPodcast>,
      source: ShowSource.Discovery,
    ): ShowPresenter

    /**
     * Opens [show] with stored episodes while the feed loads and followed shows in wide layouts.
     */
    fun create(show: PodcastFeed): ShowPresenter
  }

  /** Lets the caller provide a back control in its own chrome. */
  interface Model : BaseModel {
    /** Closes episode details when open. Otherwise returns to the caller's previous entry. */
    val onBack: () -> Unit
  }
}
