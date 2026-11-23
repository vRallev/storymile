package software.ralf.storymile.database

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.podcast.SavedEpisode
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.user.UserScope
import software.ralf.storymile.util.ExclusiveResourceOwners
import software.ralf.storymile.util.runChildOperation

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<PodcastDatabase>())
@ContributesBinding(UserScope::class, binding = binding<EpisodeQueueDatabase>())
@ContributesBinding(UserScope::class, binding = binding<SavedEpisodeDatabase>())
@ContributesIntoSet(UserScope::class, binding = binding<@ForScope(UserScope::class) Scoped>())
class UserPodcastDatabase(
  @ForScope(UserScope::class) private val storage: Storage,
  @ForScope(UserScope::class) private val coroutineScope: CoroutineScope,
  private val platform: DatabasePlatform,
) : PodcastDatabase, EpisodeQueueDatabase, SavedEpisodeDatabase, Scoped {
  private val database = CompletableDeferred<Session>()

  override val podcasts: Flow<List<PodcastFeed>> = channelFlow {
    operation { it.podcasts().observe().collect { rows -> send(rows.map { it.toPodcast() }) } }
  }
    .distinctUntilChanged()

  override val queuedEpisodes: Flow<List<QueuedEpisode>> = channelFlow {
    operation { it.queue().observe().collect { rows -> send(rows.map { it.toQueuedEpisode() }) } }
  }
    .distinctUntilChanged()

  override val savedEpisodes: Flow<List<SavedEpisode>> = channelFlow {
    operation {
      it.savedEpisodes().observe().collect { rows -> send(rows.map { it.toSavedEpisode() }) }
    }
  }
    .distinctUntilChanged()

  override fun onEnterScope(scope: Scope) {
    coroutineScope.launch {
      var opened: RoomPodcastDatabase? = null
      try {
        val path = storage.databasePath("podcasts")
        if (path != null) {
          owners.claim(key = path, owner = coroutineContext.job).forEach { it.join() }
        }
        // Let canceled Room operations finish rolling back before closing the database.
        supervisorScope {
          val room = platform.open(path = path, coroutineContext = coroutineContext)
          opened = room
          database.complete(Session(database = room, scope = this))
          awaitCancellation()
        }
      } catch (exception: Throwable) {
        database.completeExceptionally(exception)
        if (exception is CancellationException) {
          throw exception
        }
      } finally {
        opened?.let { withContext(NonCancellable) { platform.close(it) } }
      }
    }
  }

  override suspend fun insert(podcast: PodcastFeed) = operation { it.follow(podcast) }

  override suspend fun delete(feedUrl: String) = operation { it.unfollow(feedUrl) }

  override suspend fun enqueue(episode: QueuedEpisode, preferredPosition: Long?) = operation {
    it.enqueue(queued = episode, position = preferredPosition)
  }

  override suspend fun removeFromQueue(feedUrl: String, episodeId: String) = operation {
    it.removeFromQueue(feedUrl = feedUrl, episodeId = episodeId)
  }

  override suspend fun moveInQueue(
    feedUrl: String,
    episodeId: String,
    targetFeedUrl: String,
    targetEpisodeId: String,
  ) = operation {
    it.moveInQueue(
      feedUrl = feedUrl,
      episodeId = episodeId,
      targetFeedUrl = targetFeedUrl,
      targetEpisodeId = targetEpisodeId,
    )
  }

  override suspend fun save(episode: SavedEpisode, previousId: Long?) = operation {
    it.save(saved = episode, previousId = previousId)
  }

  override suspend fun removeSavedEpisode(feedUrl: String, episodeId: String) = operation {
    it.removeSavedEpisode(feedUrl = feedUrl, episodeId = episodeId)
  }

  private suspend fun <T> operation(block: suspend (RoomPodcastDatabase) -> T): T {
    coroutineScope.coroutineContext.ensureActive()
    currentCoroutineContext().ensureActive()
    val session = database.await()
    return session.scope.runChildOperation { block(session.database) }
  }

  private companion object {
    val owners = ExclusiveResourceOwners("Database path")
  }

  private data class Session(val database: RoomPodcastDatabase, val scope: CoroutineScope)
}
