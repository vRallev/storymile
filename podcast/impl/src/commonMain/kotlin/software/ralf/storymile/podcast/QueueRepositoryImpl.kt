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
import software.ralf.storymile.user.UserScope

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class QueueRepositoryImpl(
  private val database: Lazy<EpisodeQueueDatabase>,
  @ForScope(UserScope::class) coroutineScope: CoroutineScope,
) : QueueRepository {
  private val changes =
    UserDatabaseChanges(
      data = flow { emitAll(database.value.queuedEpisodes) },
      coroutineScope = coroutineScope,
      readError = "Could not load the episode queue.",
      writeError = "Could not update the episode queue.",
    )

  override val episodes: StateFlow<List<QueuedEpisode>?> = changes.state

  override fun save(episode: QueuedEpisode) {
    changes.submit { database.value.enqueue(episode) }
  }

  override fun remove(episode: QueuedEpisode) {
    changes.submit {
      database.value.removeFromQueue(feedUrl = episode.feedUrl, episodeId = episode.episode.id)
    }
  }

  override fun removeWithUndo(episode: QueuedEpisode, onRemoved: (undo: () -> Unit) -> Unit) {
    changes.submit {
      val position =
        database.value.removeFromQueue(feedUrl = episode.feedUrl, episodeId = episode.episode.id)
          ?: return@submit
      onRemoved {
        changes.submit { database.value.enqueue(episode = episode, preferredPosition = position) }
      }
    }
  }

  override fun move(episode: QueuedEpisode, target: QueuedEpisode) {
    changes.submit {
      database.value.moveInQueue(
        feedUrl = episode.feedUrl,
        episodeId = episode.episode.id,
        targetFeedUrl = target.feedUrl,
        targetEpisodeId = target.episode.id,
      )
    }
  }
}
