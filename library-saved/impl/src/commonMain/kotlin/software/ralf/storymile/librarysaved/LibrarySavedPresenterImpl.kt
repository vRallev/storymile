package software.ralf.storymile.librarysaved

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import software.ralf.storymile.podcast.SavedEpisode
import software.ralf.storymile.podcast.SavedEpisodeRepository
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class LibrarySavedPresenterImpl(
  private val savedEpisodes: SavedEpisodeRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
) : LibrarySavedPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val listPresenter = remember { ComposePresenter<Unit, ListModel> { presentList() } }
    return presenterBackstack(listPresenter) { models ->
      val list = models.first() as ListModel
      val opened = models.last() as? DetailsModel
      val removed = opened != null && list.episodes?.none { it.key == opened.episodeKey } == true
      if (removed) {
        val destination = backstack.last()
        LaunchedEffect(destination) {
          if (backstack.last() === destination) {
            pop()
          }
        }
      }
      val selection = opened.takeUnless { removed }
      val details =
        selection
          ?: list.episodes?.firstOrNull()?.let { first ->
            key(first.key) {
              val preview = remember(first) { episodeDetailsFactory.create(first) }
              DetailsModel(episodeKey = first.key, content = preview.present(Unit))
            }
          }
      Model(
        isEmpty = list.episodes?.isEmpty() == true,
        list = list.copy(selection = details),
        openedEpisode = selection?.content,
        onCloseEpisode = { pop() },
      )
    }
  }

  @Composable
  private fun presentList(): ListModel {
    val backstack = LocalBackstackScope.requireNotNull()
    val saved by savedEpisodes.episodes.collectAsState()
    val episodes =
      remember(saved) {
        saved?.map {
          EpisodeDetails(
            feedUrl = it.feedUrl,
            podcastTitle = it.podcastTitle,
            artworkUrl = it.artworkUrl,
            episode = it.episode,
          )
        }
      }
    return ListModel(
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
      onRemove = { savedEpisodes.remove(it.toSavedEpisode()) },
    )
  }

  private fun EpisodeDetails.toSavedEpisode(): SavedEpisode =
    SavedEpisode(
      feedUrl = feedUrl,
      podcastTitle = podcastTitle,
      artworkUrl = artworkUrl,
      episode = episode,
    )

  data class DetailsModel(val episodeKey: String, val content: BaseModel) : BaseModel

  /** Renderer state supplies the list and the episode to preview beside it. */
  data class ListModel(
    val episodes: List<EpisodeDetails>?,
    val onSelect: (EpisodeDetails) -> Unit,
    val onRemove: (EpisodeDetails) -> Unit,
    val selection: DetailsModel? = null,
  ) : BaseModel

  data class Model(
    override val isEmpty: Boolean,
    override val list: BaseModel,
    override val openedEpisode: BaseModel?,
    override val onCloseEpisode: () -> Unit,
  ) : LibrarySavedPresenter.Model
}
