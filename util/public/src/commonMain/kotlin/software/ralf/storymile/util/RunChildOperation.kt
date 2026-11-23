package software.ralf.storymile.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs [block] as a child of this scope with the caller's remaining coroutine context. Cancellation
 * of either owner or caller cancels the operation. Failures propagate to the caller.
 */
suspend fun <T> CoroutineScope.runChildOperation(block: suspend () -> T): T {
  val caller = currentCoroutineContext()
  caller.ensureActive()
  val operation =
    async(caller.minusKey(Job)) {
      try {
        Result.success(block())
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
