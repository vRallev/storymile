package software.ralf.storymile.database

import androidx.room3.withWriteTransaction
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
import kotlinx.coroutines.withContext
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.user.UserScope

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, binding = binding<PodcastDatabase>())
@ContributesBinding(UserScope::class, binding = binding<EpisodeQueueDatabase>())
@ContributesIntoSet(UserScope::class, binding = binding<@ForScope(UserScope::class) Scoped>())
class UserPodcastDatabase(
  @ForScope(UserScope::class) private val storage: Storage,
  @ForScope(UserScope::class) private val coroutineScope: CoroutineScope,
  private val platform: DatabasePlatform,
) : PodcastDatabase, EpisodeQueueDatabase, Scoped {
  private val database = CompletableDeferred<RoomPodcastDatabase>()

  override val podcasts: Flow<List<PodcastFeed>> = channelFlow {
    operation { it.podcasts().observe().collect { rows -> send(rows.map { it.toPodcast() }) } }
  }
    .distinctUntilChanged()

  override val queuedEpisodes: Flow<List<QueuedEpisode>> = channelFlow {
    operation { it.queue().observe().collect { rows -> send(rows.map { it.toQueuedEpisode() }) } }
  }
    .distinctUntilChanged()

  override fun onEnterScope(scope: Scope) {
    coroutineScope.launch {
      var opened: RoomPodcastDatabase? = null
      try {
        val path = storage.databasePath("podcasts")
        awaitPreviousOwner(path)
        opened =
          platform.open(
            path = path,
            coroutineContext = coroutineScope.coroutineContext,
          )
        database.complete(opened)
        awaitCancellation()
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

  override suspend fun insert(podcast: PodcastFeed) = operation { it.podcasts().insert(podcast) }

  override suspend fun delete(feedUrl: String) = operation { it.podcasts().delete(feedUrl) }

  override suspend fun enqueue(episode: QueuedEpisode) = operation { database ->
    database.withWriteTransaction {
      val episodeId = database.podcasts().ensureEpisode(episode)
      database.queue().append(episodeId)
    }
  }

  override suspend fun removeFromQueue(feedUrl: String, episodeId: String) = operation { database ->
    database.withWriteTransaction {
      val podcasts = database.podcasts()
      val showId = podcasts.findShowId(feedUrl) ?: return@withWriteTransaction
      if (database.queue().delete(showId = showId, sourceEpisodeId = episodeId) > 0) {
        podcasts.pruneUnretained(showId)
      }
    }
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
    val operation =
      coroutineScope.async(callerContext.minusKey(Job)) {
        try {
          Result.success(block(database.await()))
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
}
