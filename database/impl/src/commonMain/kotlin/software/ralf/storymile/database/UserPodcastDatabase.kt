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
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.updateAndGet
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
        awaitPreviousOwner(path)
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

  private suspend fun awaitPreviousOwner(path: String?) {
    if (path == null) {
      return
    }
    val owner = currentCoroutineContext().job
    val previous =
      owners
        .updateAndGet { paths ->
          owner.ensureActive()
          val previous = paths[path].orEmpty().filterNot { it.isCompleted }
          check(previous.none { it.isActive }) {
            "Database path is already owned by another active scope."
          }
          paths.filterValues { owners -> owners.any { !it.isCompleted } } +
            (path to (previous + owner))
        }
        .getValue(path)
        .dropLast(1)
    previous.forEach { it.join() }
  }

  private suspend fun <T> operation(block: suspend (RoomPodcastDatabase) -> T): T {
    coroutineScope.coroutineContext.ensureActive()
    val callerContext = currentCoroutineContext()
    callerContext.ensureActive()
    val session = database.await()
    val operation =
      session.scope.async(callerContext.minusKey(Job)) {
        try {
          Result.success(block(session.database))
        } catch (exception: Throwable) {
          Result.failure(exception)
        }
      }
    return try {
      operation.await().getOrThrow()
    } finally {
      operation.cancel()
    }
  }

  private companion object {
    val owners = MutableStateFlow<Map<String, List<Job>>>(emptyMap())
  }

  private data class Session(val database: RoomPodcastDatabase, val scope: CoroutineScope)
}
