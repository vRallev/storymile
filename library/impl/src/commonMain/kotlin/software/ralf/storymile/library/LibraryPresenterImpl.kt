package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackModel
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.discovery.DiscoveryPresenter
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.show.ShowPresenter
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class, binding = binding<LibraryPresenter<*>>())
class LibraryPresenterImpl(
  private val discoveryPresenterFactory: DiscoveryPresenter.Factory,
  private val showFactory: ShowPresenter.Factory,
  private val database: PodcastDatabase,
) : LibraryPresenter<LibraryPresenterImpl.NavigationModel> {
  @Composable
  override fun present(input: Unit): NavigationModel {
    var selectedTab by
      rememberSaveable(
        stateSaver = Saver(save = { tab: Tab -> tab.name }, restore = { Tab.valueOf(it) }),
      ) {
        mutableStateOf(Tab.QUEUE)
      }
    val contentPresenter = remember {
      ComposePresenter<Unit, Model> {
        val backstack = LocalBackstackScope.requireNotNull()
        var retry by remember { mutableIntStateOf(0) }
        val shows = followedShows(retry) { retry++ }
        var selection by remember { mutableStateOf<Selection?>(null) }
        val openShow = selection?.takeIf { it.presenter === backstack.backstack.last() }
        Model(
          selectedTab = selectedTab,
          onSelectTab = {
            if (it != selectedTab && openShow != null) {
              backstack.pop()
            }
            selectedTab = it
          },
          onDiscoverPodcasts = { backstack.push(discoveryPresenterFactory.create()) },
          shows = shows,
          selectedShowUrl = openShow?.feedUrl,
          onShowSelected = { feed ->
            if (feed.feedUrl != openShow?.feedUrl) {
              val presenter = showFactory.create(feed)
              selection = Selection(feedUrl = feed.feedUrl, presenter = presenter)
              if (openShow == null) {
                backstack.push(presenter)
              } else {
                backstack.replaceTop(presenter)
              }
            }
          },
        )
      }
    }
    return presenterBackstack(contentPresenter) { entries ->
      NavigationModel(backstack = entries, onBack = { pop() })
    }
  }

  @Composable
  private fun followedShows(retry: Int, onRetry: () -> Unit): FollowedShowsState =
    key(retry) {
      val state by
        produceState<FollowedShowsState>(FollowedShowsState.Loading) {
          try {
            database.podcasts.collect { value = FollowedShowsState.Ready(it) }
          } catch (exception: Exception) {
            if (exception is CancellationException) {
              throw exception
            }
            value = FollowedShowsState.Error(onRetry)
          }
        }
      state
    }

  enum class Tab {
    QUEUE,
    SHOWS,
    SAVED,
    HISTORY,
  }

  private data class Selection(val feedUrl: String, val presenter: ShowPresenter<*>)

  sealed interface FollowedShowsState {
    data object Loading : FollowedShowsState

    data class Ready(val feeds: List<PodcastFeed>) : FollowedShowsState

    data class Error(val onRetry: () -> Unit) : FollowedShowsState
  }

  data class NavigationModel(
    override val backstack: List<BaseModel>,
    override val onBack: () -> Unit,
  ) : PresenterBackstackModel

  data class Model(
    val selectedTab: Tab,
    val onSelectTab: (Tab) -> Unit,
    val onDiscoverPodcasts: () -> Unit,
    val shows: FollowedShowsState,
    val selectedShowUrl: String?,
    val onShowSelected: (PodcastFeed) -> Unit,
  ) : BaseModel
}
