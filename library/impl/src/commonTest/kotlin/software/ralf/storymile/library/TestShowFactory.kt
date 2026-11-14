package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.show.ShowOrigin
import software.ralf.storymile.show.ShowPresenter

internal object TestShowFactory : ShowPresenter.Factory {
  override fun create(feed: PodcastFeed): ShowPresenter<*> =
    object : ShowPresenter<Model> {
      @Composable override fun present(input: Unit): Model = Model(feed)
    }

  override fun create(
    podcast: DiscoveredPodcast,
    catalog: List<DiscoveredPodcast>,
    source: ShowOrigin,
  ): ShowPresenter<*> = error("Discovery does not open a show in this fixture")

  data class Model(val feed: PodcastFeed) : ShowPresenter.Model
}
