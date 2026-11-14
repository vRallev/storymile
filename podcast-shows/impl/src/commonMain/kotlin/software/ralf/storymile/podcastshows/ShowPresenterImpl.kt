package software.ralf.storymile.podcastshows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackModel
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.podcast.SavedShowRepository

class ShowPresenterImpl(
  private val catalog: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val savedShows: SavedShowRepository,
  private val selection: ShowSelection,
  private val source: ShowSource,
) : ShowPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val parentBackstack = LocalBackstackScope.requireNotNull()
    val content = remember {
      ShowContentPresenter(
        catalog = catalog,
        feeds = feeds,
        episodeDetailsFactory = episodeDetailsFactory,
        savedShows = savedShows,
        selection = selection,
        source = source,
        onBack = { parentBackstack.pop() },
      )
    }
    return presenterBackstack(content) { models ->
      Model(
        backstack = models,
        onBack = {
          if (backstack.size > 1) {
            pop()
          } else {
            parentBackstack.pop()
          }
        },
      )
    }
  }

  /** [content] supplies the root show and sidebar data for the renderer. */
  data class Model(
    override val backstack: List<BaseModel>,
    override val onBack: () -> Unit,
  ) : ShowPresenter.Model, PresenterBackstackModel {
    val content: ShowContentPresenter.Model
      get() = backstack.first() as ShowContentPresenter.Model
  }
}
