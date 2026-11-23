package software.ralf.storymile.episodedetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.podcast.SavedEpisode
import software.ralf.storymile.podcast.SavedEpisodeRepository
import software.ralf.storymile.user.LocalOpenSettings

class EpisodeDetailsPresenterImpl(
  private val details: EpisodeDetails,
  private val queue: QueueRepository,
  private val savedEpisodes: SavedEpisodeRepository,
) : EpisodeDetailsPresenter<EpisodeDetailsPresenterImpl.Model> {
  @Composable
  override fun present(input: Unit): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val queued =
      queue.episodes.membership {
        it.feedUrl == details.feedUrl && it.episode.id == details.episode.id
      }
    val saved =
      savedEpisodes.episodes.membership {
        it.feedUrl == details.feedUrl && it.episode.id == details.episode.id
      }
    return Model(
      details = details,
      onPlayNow = {},
      onToggleQueue = {
        when {
          !queued.loaded -> Unit
          queued.episode != null -> queue.remove(queued.episode)
          else ->
            queue.save(
              QueuedEpisode(
                feedUrl = details.feedUrl,
                podcastTitle = details.podcastTitle,
                artworkUrl = details.artworkUrl,
                episode = details.episode,
              ),
            )
        }
      },
      onToggleSave = {
        when {
          !saved.loaded -> Unit
          saved.episode != null -> savedEpisodes.remove(saved.episode)
          else ->
            savedEpisodes.save(
              SavedEpisode(
                feedUrl = details.feedUrl,
                podcastTitle = details.podcastTitle,
                artworkUrl = details.artworkUrl,
                episode = details.episode,
              ),
            )
        }
      },
      onDownload = {},
      onMore = {},
      onBack = { backstack.pop() },
      onOpenSettings = LocalOpenSettings.current,
      isQueued = queued.episode != null,
      queueEnabled = queued.loaded,
      isSaved = saved.episode != null,
      saveEnabled = saved.loaded,
    )
  }

  @Composable
  private fun <T> StateFlow<List<T>?>.membership(matches: (T) -> Boolean): Membership<T> {
    val episodes = collectAsState()
    return remember(episodes, matches) {
        derivedStateOf(structuralEqualityPolicy()) {
          val entries = episodes.value
          Membership(loaded = entries != null, episode = entries?.firstOrNull(matches))
        }
      }
      .value
  }

  private data class Membership<T>(val loaded: Boolean, val episode: T?)

  /** Renderer state retains the selected episode snapshot. */
  data class Model(
    val details: EpisodeDetails,
    val onPlayNow: () -> Unit,
    val onToggleQueue: () -> Unit,
    val onToggleSave: () -> Unit,
    val onDownload: () -> Unit,
    val onMore: () -> Unit,
    val onBack: () -> Unit,
    val isQueued: Boolean = false,
    val queueEnabled: Boolean = true,
    val isSaved: Boolean = false,
    val saveEnabled: Boolean = true,
    val onOpenSettings: (() -> Unit)? = null,
  ) : BaseModel
}
