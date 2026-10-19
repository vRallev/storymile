package software.ralf.storymile.episodedetails

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class EpisodeDetailsPresenterFactory : EpisodeDetailsPresenter.Factory {
  override fun create(details: EpisodeDetails): EpisodeDetailsPresenter<*> =
    EpisodeDetailsPresenterImpl(details)
}
