package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcastshows.ShowPresenter
import software.ralf.storymile.podcastshows.ShowSource

internal class TestShowPresenterFactory : ShowPresenter.Factory {
  val opened = mutableListOf<Opened>()
  val presenter =
    object : ShowPresenter {
      @Composable override fun present(input: Unit): ShowPresenter.Model = Model
    }

  override fun create(
    podcast: DiscoveredPodcast,
    catalog: List<DiscoveredPodcast>,
    source: ShowSource.Discovery,
  ): ShowPresenter {
    opened += Opened(podcast = podcast, catalog = catalog, source = source)
    return presenter
  }

  override fun create(show: PodcastFeed): ShowPresenter = error("Unexpected saved show")

  data class Opened(
    val podcast: DiscoveredPodcast,
    val catalog: List<DiscoveredPodcast>,
    val source: ShowSource.Discovery,
  )

  private data object Model : ShowPresenter.Model {
    override val onBack: () -> Unit = {}
  }
}
