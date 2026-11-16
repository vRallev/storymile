package software.ralf.storymile.podcast

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.ralf.storymile.database.EpisodeQueueDatabase
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.user.UserScope

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class QueueRepositoryImpl(
  private val database: EpisodeQueueDatabase,
  private val runtimeModeController: RuntimeModeController,
  @ForScope(UserScope::class) coroutineScope: CoroutineScope,
) : QueueRepository {
  private val initialization = Mutex()
  private var initialized = false
  private val changes =
    UserDatabaseChanges(
      data = database.queuedEpisodes.onStart { initialize() },
      coroutineScope = coroutineScope,
      readError = "Could not load the episode queue.",
      writeError = "Could not update the episode queue.",
    )

  override val episodes: StateFlow<List<QueuedEpisode>?> = changes.state

  override fun save(episode: QueuedEpisode) {
    submit { database.enqueue(episode) }
  }

  override fun remove(episode: QueuedEpisode) {
    submit {
      database.removeFromQueue(feedUrl = episode.feedUrl, episodeId = episode.episode.id)
    }
  }

  override fun move(episode: QueuedEpisode, target: QueuedEpisode) {
    submit {
      database.moveInQueue(
        feedUrl = episode.feedUrl,
        episodeId = episode.episode.id,
        targetFeedUrl = target.feedUrl,
        targetEpisodeId = target.episode.id,
      )
    }
  }

  private fun submit(write: suspend () -> Unit) {
    changes.submit {
      initialize()
      write()
    }
  }

  private suspend fun initialize() {
    initialization.withLock {
      if (initialized) {
        return
      }
      if (runtimeModeController.mode == RuntimeMode.Fake) {
        DemoEpisodes.queue.forEach { database.enqueue(it) }
      }
      initialized = true
    }
  }
}
