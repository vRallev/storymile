package software.ralf.storymile.show

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackModel
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.user.UserScope
import software.ralf.storymile.util.HtmlDescription

class ShowPresenterImpl(
  private val catalog: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val database: PodcastDatabase,
  private val initialPodcast: DiscoveredPodcast,
  private val podcasts: List<DiscoveredPodcast>,
  private val source: ShowOrigin,
  private val savedFeed: PodcastFeed? = null,
) : ShowPresenter<ShowPresenterImpl.Model> {
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
        savedFeed = savedFeed,
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

  @Inject
  @ContributesBinding(UserScope::class)
  class Factory(
    private val catalog: PodcastDiscoveryRepository,
    private val feeds: PodcastFeedRepository,
    private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
    private val database: PodcastDatabase,
  ) : ShowPresenter.Factory {
    override fun create(
      podcast: DiscoveredPodcast,
      catalog: List<DiscoveredPodcast>,
      source: ShowOrigin,
    ): ShowPresenter<*> =
      ShowPresenterImpl(
        catalog = this.catalog,
        feeds = feeds,
        episodeDetailsFactory = episodeDetailsFactory,
        database = database,
        initialPodcast = podcast,
        podcasts = catalog,
        source = source,
      )

    override fun create(feed: PodcastFeed): ShowPresenter<*> =
      ShowPresenterImpl(
        catalog = catalog,
        feeds = feeds,
        episodeDetailsFactory = episodeDetailsFactory,
        database = database,
        initialPodcast =
          DiscoveredPodcast(
            id = feed.feedUrl,
            title = feed.title,
            author = feed.author,
            feedUrl = feed.feedUrl,
            artworkUrl = feed.artworkUrl,
            catalogUrl = null,
            description = HtmlDescription.plainText(feed.description),
          ),
        podcasts = emptyList(),
        source = ShowOrigin.Library,
        savedFeed = feed,
      )
  }

  data class Model(
    override val backstack: List<BaseModel>,
    override val onBack: () -> Unit,
  ) : PresenterBackstackModel, ShowPresenter.Model {
    val content: ShowContentPresenter.Model
      get() = backstack.first() as ShowContentPresenter.Model
  }
}
