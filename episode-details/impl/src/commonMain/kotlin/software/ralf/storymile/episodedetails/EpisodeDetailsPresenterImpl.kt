package software.ralf.storymile.episodedetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode

class EpisodeDetailsPresenterImpl(
  private val details: EpisodeDetails,
  private val queue: QueueRepository,
) : EpisodeDetailsPresenter<EpisodeDetailsPresenterImpl.Model> {
  @Composable
  override fun present(input: Unit): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val queued by queue.episodes.collectAsState()
    val queueEnabled = queued != null
    val queuedEpisode = queued?.firstOrNull {
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
      onSave = {},
      onDownload = {},
      onMore = {},
      onBack = { backstack.pop() },
      isQueued = queuedEpisode != null,
      queueEnabled = queueEnabled,
    )
  }

  /** Renderer state retains the selected episode snapshot. */
  data class Model(
    val details: EpisodeDetails,
    val onPlayNow: () -> Unit,
    val onToggleQueue: () -> Unit,
    val onSave: () -> Unit,
    val onDownload: () -> Unit,
    val onMore: () -> Unit,
    val onBack: () -> Unit,
    val isQueued: Boolean = false,
    val queueEnabled: Boolean = true,
  ) : BaseModel
}
