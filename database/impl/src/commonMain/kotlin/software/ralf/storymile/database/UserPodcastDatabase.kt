package software.ralf.storymile.database

import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.user.UserScope

@SingleIn(UserScope::class)
@ContributesScoped(UserScope::class)
class UserPodcastDatabase(
  @ForScope(UserScope::class) private val storage: Storage,
  @ForScope(UserScope::class) private val coroutineScope: CoroutineScope,
  private val platform: DatabasePlatform,
) : PodcastDatabase, Scoped {
  private val database = CompletableDeferred<RoomPodcastDatabase>()

  override val podcasts: Flow<List<PodcastFeed>> = channelFlow {
    operation { dao -> dao.observe().collect { rows -> send(rows.map { it.toPodcast() }) } }
  }
    .distinctUntilChanged()

  override fun onEnterScope(scope: Scope) {
    coroutineScope.launch {
      var opened: RoomPodcastDatabase? = null
      try {
        opened =
          platform.open(
            path = storage.databasePath("podcasts"),
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

  override suspend fun insert(podcast: PodcastFeed) = operation { it.insert(podcast) }

  private suspend fun <T> operation(block: suspend (PodcastDao) -> T): T {
    coroutineScope.coroutineContext.ensureActive()
    val callerContext = currentCoroutineContext()
    callerContext.ensureActive()
    val operation =
      coroutineScope.async(callerContext.minusKey(Job)) {
        try {
          Result.success(block(database.await().podcasts()))
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
}
