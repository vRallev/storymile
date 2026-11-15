package software.ralf.storymile.libraryshows

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
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.SavedShowRepository
import software.ralf.storymile.podcastshows.ShowPresenter
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class LibraryShowsPresenterImpl(
  private val savedShows: SavedShowRepository,
  private val showPresenterFactory: ShowPresenter.Factory,
) : LibraryShowsPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val list = remember {
      ComposePresenter<Unit, ListModel> {
        val backstack = LocalBackstackScope.requireNotNull()
        val saved by savedShows.shows.collectAsState()
        val shows = remember(saved) { saved?.sortedBy { it.title.lowercase() } }
        ListModel(
          shows = shows,
          onSelect = {
            if (backstack.backstack.size == 1) {
              backstack.push(showPresenterFactory.create(it))
            }
          },
        )
      }
    }
    return presenterBackstack(list) { models ->
      val listModel = models.first() as ListModel
      val selectedShow = models.last() as? ShowPresenter.Model
      Model(
        isEmpty = listModel.shows?.isEmpty() == true,
        list = listModel,
        selectedShow = selectedShow,
        onOpenFirstShow =
          listModel.shows?.firstOrNull()?.let { show -> { listModel.onSelect(show) } },
        onCloseEpisode = selectedShow?.onCloseEpisode,
        onCloseShow = { pop() },
      )
    }
  }

  data class Model(
    override val isEmpty: Boolean,
    override val list: BaseModel,
    override val selectedShow: BaseModel?,
    override val onOpenFirstShow: (() -> Unit)?,
    override val onCloseEpisode: (() -> Unit)?,
    override val onCloseShow: () -> Unit,
  ) : LibraryShowsPresenter.Model

  data class ListModel(val shows: List<PodcastFeed>?, val onSelect: (PodcastFeed) -> Unit) :
    BaseModel
}
