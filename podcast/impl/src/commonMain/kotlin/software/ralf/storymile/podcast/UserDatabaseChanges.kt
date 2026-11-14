package software.ralf.storymile.podcast

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import software.ralf.storymile.logging.Logger.Companion.logger

internal class UserDatabaseChanges<T>(
  data: Flow<T>,
  coroutineScope: CoroutineScope,
  readError: String,
  private val writeError: String,
) {
  val state: StateFlow<T?> =
    data
      .retryWhen { cause, _ ->
        if (cause is CancellationException || cause !is Exception) {
          throw cause
        }
        logger.e(cause) { readError }
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

  fun submit(write: suspend () -> Unit) {
    writes.trySend(write)
  }

  private suspend fun persist(write: suspend () -> Unit) {
    try {
      write()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      logger.e(exception) { writeError }
    }
  }
}
