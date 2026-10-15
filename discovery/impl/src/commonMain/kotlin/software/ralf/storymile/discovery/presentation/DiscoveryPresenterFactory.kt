package software.ralf.storymile.discovery.presentation

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.storymile.discovery.DiscoveryPresenter
import software.ralf.storymile.discovery.PodcastDiscoveryService
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class DiscoveryPresenterFactory(private val service: PodcastDiscoveryService) :
  DiscoveryPresenter.Factory {
  override fun create(focusSearch: Boolean): DiscoveryPresenter<*> =
    DiscoveryPresenterImpl(service = service, focusSearch = focusSearch)
}
