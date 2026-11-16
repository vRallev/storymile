package software.ralf.storymile.libraryqueue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class LibraryQueuePresenterImpl(
  private val queue: QueueRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
) : LibraryQueuePresenter {
  @Composable
  override fun present(input: Unit): Model {
    val listPresenter = remember {
      ComposePresenter<Unit, ListModel> {
        val backstack = LocalBackstackScope.requireNotNull()
        val queued by queue.episodes.collectAsState()
        val episodes =
          remember(queued) {
            queued?.map {
              EpisodeDetails(
                feedUrl = it.feedUrl,
                podcastTitle = it.podcastTitle,
                artworkUrl = it.artworkUrl,
                episode = it.episode,
              )
            }
          }
        ListModel(
          episodes = episodes,
          onSelect = { episode ->
            val details = episodeDetailsFactory.create(episode)
            val destination =
              ComposePresenter<Unit, DetailsModel> {
                DetailsModel(episodeKey = episode.key, content = details.present(Unit))
              }
            if (backstack.backstack.size == 1) {
              backstack.push(destination)
            } else {
              backstack.replaceTop(destination)
            }
          },
          onReorder = { episode, target ->
            queue.move(episode = episode.toQueuedEpisode(), target = target.toQueuedEpisode())
          },
          onRemove = { episode ->
            queue.remove(episode.toQueuedEpisode())
          },
        )
      }
    }
    return presenterBackstack(listPresenter) { models ->
      val list = models.first() as ListModel
      val opened = models.last() as? DetailsModel
      val details =
        opened
          ?: list.episodes?.firstOrNull()?.let { first ->
            val preview = remember(first) { episodeDetailsFactory.create(first) }
            DetailsModel(episodeKey = first.key, content = preview.present(Unit))
          }
      Model(
        isEmpty = list.episodes?.isEmpty() == true,
        list = list.copy(selection = details),
        openedEpisode = opened?.content,
        onCloseEpisode = { pop() },
      )
    }
  }

  private fun EpisodeDetails.toQueuedEpisode(): QueuedEpisode =
    QueuedEpisode(
      feedUrl = feedUrl,
      podcastTitle = podcastTitle,
      artworkUrl = artworkUrl,
      episode = episode,
    )

  data class Model(
    override val isEmpty: Boolean,
    override val list: BaseModel,
    override val openedEpisode: BaseModel?,
    override val onCloseEpisode: () -> Unit,
  ) : LibraryQueuePresenter.Model

  data class ListModel(
    val episodes: List<EpisodeDetails>?,
    val onSelect: (EpisodeDetails) -> Unit,
    val onReorder: (episode: EpisodeDetails, target: EpisodeDetails) -> Unit,
    val onRemove: (EpisodeDetails) -> Unit,
    val selection: DetailsModel? = null,
  ) : BaseModel

  data class DetailsModel(val episodeKey: String, val content: BaseModel) : BaseModel
}
