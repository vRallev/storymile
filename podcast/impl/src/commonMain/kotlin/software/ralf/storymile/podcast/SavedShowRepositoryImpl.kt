package software.ralf.storymile.podcast

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.logging.Logger.Companion.logger
import software.ralf.storymile.user.UserScope

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class SavedShowRepositoryImpl(
  private val database: PodcastDatabase,
  @ForScope(UserScope::class) coroutineScope: CoroutineScope,
) : SavedShowRepository {
  override val shows: StateFlow<List<PodcastFeed>?> =
    database.podcasts
      .retryWhen { cause, _ ->
        if (cause is CancellationException || cause !is Exception) {
          throw cause
        }
        logger.e(cause) { "Could not load followed shows." }
        delay(5.seconds)
        true
      }
      .stateIn(scope = coroutineScope, started = SharingStarted.Lazily, initialValue = null)

  private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

  init {
    coroutineScope
      .launch {
        for (write in writes) {
          persist(write)
        }
      }
      .invokeOnCompletion { writes.cancel() }
  }

  override fun save(show: PodcastFeed) {
    writes.trySend { database.insert(show) }
  }

  override fun remove(feedUrl: String) {
    writes.trySend { database.delete(feedUrl) }
  }

  private suspend fun persist(write: suspend () -> Unit) {
    try {
      write()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      logger.e(exception) { "Could not update a followed show." }
    }
  }
}
