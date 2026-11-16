package software.ralf.storymile.podcast

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import software.ralf.storymile.database.EpisodeQueueDatabase
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.user.UserScope

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class QueueRepositoryImpl(
  database: Lazy<EpisodeQueueDatabase>,
  runtimeModeController: RuntimeModeController,
  @ForScope(UserScope::class) coroutineScope: CoroutineScope,
) : QueueRepository {
  private val backend by lazy {
    if (runtimeModeController.mode == RuntimeMode.Fake) FakeEpisodeQueue() else database.value
  }
  private val changes =
    UserDatabaseChanges(
      data = flow { emitAll(backend.queuedEpisodes) },
      coroutineScope = coroutineScope,
      readError = "Could not load the episode queue.",
      writeError = "Could not update the episode queue.",
    )

  override val episodes: StateFlow<List<QueuedEpisode>?> = changes.state

  override fun save(episode: QueuedEpisode) {
    changes.submit { backend.enqueue(episode) }
  }

  override fun remove(episode: QueuedEpisode) {
    changes.submit {
      backend.removeFromQueue(feedUrl = episode.feedUrl, episodeId = episode.episode.id)
    }
  }

  override fun removeWithUndo(episode: QueuedEpisode, onRemoved: (undo: () -> Unit) -> Unit) {
    changes.submit {
      val position =
        backend.removeFromQueue(feedUrl = episode.feedUrl, episodeId = episode.episode.id)
          ?: return@submit
      onRemoved {
        changes.submit { backend.enqueue(episode = episode, preferredPosition = position) }
      }
    }
  }

  override fun move(episode: QueuedEpisode, target: QueuedEpisode) {
    changes.submit {
      backend.moveInQueue(
        feedUrl = episode.feedUrl,
        episodeId = episode.episode.id,
        targetFeedUrl = target.feedUrl,
        targetEpisodeId = target.episode.id,
      )
    }
  }
}
