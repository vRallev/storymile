package software.ralf.storymile.podcast

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.database.EpisodeQueueDatabase
import software.ralf.storymile.user.UserScope

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class EpisodeQueueRepositoryImpl(
  private val database: EpisodeQueueDatabase,
  @ForScope(UserScope::class) coroutineScope: CoroutineScope,
) : EpisodeQueueRepository {
  private val changes =
    UserDatabaseChanges(
      data = database.queuedEpisodes,
      coroutineScope = coroutineScope,
      readError = "Could not load the episode queue.",
      writeError = "Could not update the episode queue.",
    )

  override val episodes: StateFlow<List<QueuedEpisode>?> = changes.state

  override fun save(episode: QueuedEpisode) {
    changes.submit { database.enqueue(episode) }
  }

  override fun remove(feedUrl: String, episodeId: String) {
    changes.submit { database.removeFromQueue(feedUrl = feedUrl, episodeId = episodeId) }
  }
}
