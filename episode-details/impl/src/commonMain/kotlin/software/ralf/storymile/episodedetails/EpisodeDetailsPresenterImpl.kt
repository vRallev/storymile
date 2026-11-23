package software.ralf.storymile.episodedetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val queued by queue.episodes.collectAsState()
    val queueEnabled = queued != null
    val queuedEpisode = queued?.firstOrNull {
      it.feedUrl == details.feedUrl && it.episode.id == details.episode.id
    }
    val saved by savedEpisodes.episodes.collectAsState()
    val saveEnabled = saved != null
    val savedEpisode = saved?.firstOrNull {
      it.feedUrl == details.feedUrl && it.episode.id == details.episode.id
    }
    return Model(
      details = details,
      onPlayNow = {},
      onToggleQueue = {
        when {
          !queueEnabled -> Unit
          queuedEpisode != null -> queue.remove(queuedEpisode)
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
          !saveEnabled -> Unit
          savedEpisode != null -> savedEpisodes.remove(savedEpisode)
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
      isQueued = queuedEpisode != null,
      queueEnabled = queueEnabled,
      isSaved = savedEpisode != null,
      saveEnabled = saveEnabled,
    )
  }

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
