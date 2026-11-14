package software.ralf.storymile.discovery.presentation

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.storymile.discovery.DiscoveryPresenter
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.podcastshows.ShowPresenter
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class DiscoveryPresenterFactory(
  private val repository: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val showPresenterFactory: ShowPresenter.Factory,
) : DiscoveryPresenter.Factory {
  override fun create(focusSearch: Boolean): DiscoveryPresenter<*> =
    DiscoveryPresenterImpl(
      repository = repository,
      feeds = feeds,
      episodeDetailsFactory = episodeDetailsFactory,
      showPresenterFactory = showPresenterFactory,
      focusSearch = focusSearch,
    )
}
