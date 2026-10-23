package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackModel
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeedRepository

class ShowPresenter(
  private val catalog: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val database: PodcastDatabase,
  private val initialPodcast: DiscoveredPodcast,
  private val podcasts: List<DiscoveredPodcast>,
  private val source: Source,
) : ComposePresenter<Unit, ShowPresenter.Model> {
  @Composable
  override fun present(input: Unit): Model {
    val parentBackstack = LocalBackstackScope.requireNotNull()
    val content = remember {
      ShowContentPresenter(
        catalog = catalog,
        feeds = feeds,
        episodeDetailsFactory = episodeDetailsFactory,
        database = database,
        initialPodcast = initialPodcast,
        podcasts = podcasts,
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

  sealed interface Source {
    data object Browse : Source

    data object Search : Source

    data class Topic(val topic: DiscoveryTopic) : Source
  }

  data class Model(
    override val backstack: List<BaseModel>,
    override val onBack: () -> Unit,
  ) : PresenterBackstackModel {
    val content: ShowContentPresenter.Model
      get() = backstack.first() as ShowContentPresenter.Model
  }
}
