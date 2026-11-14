package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeed

@Inject
class FollowedShowsPresenter(
  private val database: PodcastDatabase,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
) : ComposePresenter<Boolean, FollowedShowsPresenter.Model> {
  @Composable
  override fun present(input: Boolean): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val scope = rememberCoroutineScope()
    var selectedFeedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var unfollowing by remember { mutableStateOf(false) }
    var unfollowFailed by remember { mutableStateOf(false) }
    val state = if (input) observeShows(retry) else State.Loading
    val shows = (state as? State.Loaded)?.shows.orEmpty()
    val selection = shows.firstOrNull { it.feedUrl == selectedFeedUrl }
    LaunchedEffect(state, selectedFeedUrl) {
      if (state is State.Loaded && shows.none { it.feedUrl == selectedFeedUrl }) {
        selectedFeedUrl = null
      }
    }
    return Model(
      state = state,
      selectedShow = selection ?: shows.firstOrNull(),
      detailRequested = selection != null,
      canUnfollow = !unfollowing,
      unfollowFailed = unfollowFailed,
      onRetry = { retry++ },
      onSelectShow = { selectedFeedUrl = it },
      onCloseDetail = { selectedFeedUrl = null },
      onUnfollow = { feedUrl ->
        if (!unfollowing) {
          unfollowing = true
          unfollowFailed = false
          scope.launch {
            try {
              database.delete(feedUrl)
            } catch (exception: CancellationException) {
              throw exception
            } catch (_: Exception) {
              unfollowFailed = true
            } finally {
              unfollowing = false
            }
          }
        }
      },
      onOpenEpisode = { backstack.push(episodeDetailsFactory.create(it)) },
    )
  }

  @Composable
  private fun observeShows(retry: Int): State =
    key(retry) {
      val state by
        produceState<State>(State.Loading) {
          try {
            database.podcasts.collect { shows ->
              value = State.Loaded(shows.sortedBy { it.title.lowercase() })
            }
          } catch (exception: CancellationException) {
            throw exception
          } catch (_: Exception) {
            value = State.ReadFailed
          }
        }
      state
    }

  sealed interface State {
    data object Loading : State

    data object ReadFailed : State

    data class Loaded(val shows: List<PodcastFeed>) : State
  }

  data class Model(
    val state: State,
    val selectedShow: PodcastFeed?,
    val detailRequested: Boolean,
    val canUnfollow: Boolean,
    val unfollowFailed: Boolean,
    val onRetry: () -> Unit,
    val onSelectShow: (String) -> Unit,
    val onCloseDetail: () -> Unit,
    val onUnfollow: (String) -> Unit,
    val onOpenEpisode: (EpisodeDetails) -> Unit,
  ) : BaseModel
}
