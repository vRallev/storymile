package software.ralf.storymile.podcastshows

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.podcast.SavedShowRepository
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class ShowPresenterFactory(
  private val catalog: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val savedShows: SavedShowRepository,
) : ShowPresenter.Factory {
  override fun create(
    podcast: DiscoveredPodcast,
    catalog: List<DiscoveredPodcast>,
    source: ShowSource.Discovery,
  ): ShowPresenter =
    create(
      selection = ShowSelection.Discovery(initialPodcast = podcast, podcasts = catalog),
      source = source,
    )

  override fun create(show: PodcastFeed): ShowPresenter =
    create(selection = ShowSelection.Saved(show), source = ShowSource.Library)

  private fun create(selection: ShowSelection, source: ShowSource): ShowPresenter =
    ShowPresenterImpl(
      catalog = catalog,
      feeds = feeds,
      episodeDetailsFactory = episodeDetailsFactory,
      savedShows = savedShows,
      selection = selection,
      source = source,
    )
}
