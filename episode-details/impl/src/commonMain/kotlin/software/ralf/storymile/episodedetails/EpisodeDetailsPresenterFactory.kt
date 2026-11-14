package software.ralf.storymile.episodedetails

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.storymile.podcast.EpisodeQueueRepository
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class EpisodeDetailsPresenterFactory(private val queue: EpisodeQueueRepository) :
  EpisodeDetailsPresenter.Factory {
  override fun create(details: EpisodeDetails): EpisodeDetailsPresenter<*> =
    EpisodeDetailsPresenterImpl(details = details, queue = queue)
}
