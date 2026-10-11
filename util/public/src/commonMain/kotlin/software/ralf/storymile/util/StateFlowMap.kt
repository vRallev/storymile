package software.ralf.storymile.util

import kotlin.concurrent.Volatile
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.internal.SynchronizedObject
import kotlinx.coroutines.internal.synchronized

/**
 * Applies [mapper] lazily to this flow while preserving access to the current [StateFlow.value].
 *
 * The mapper cannot suspend. The last mapping is cached while the source value is the same
 * instance. Collectors receive the initial mapped value and subsequent distinct values, including
 * null.
 */
// This implementation started with the one provided below, but it wasn't complete. Our unit
// tests cover edge cases around the collect() function.
//
// https://github.com/Kotlin/kotlinx.coroutines/issues/2514#issuecomment-775001647
@OptIn(ExperimentalForInheritanceCoroutinesApi::class, InternalCoroutinesApi::class)
fun <T, R> StateFlow<T>.mapStateFlow(mapper: (value: T) -> R): StateFlow<R> {
  return object : StateFlow<R> {

    private val lock = SynchronizedObject()

    @Volatile private var valueMapping: Pair<T, R>? = null

    override val replayCache: List<R>
      get() = this@mapStateFlow.replayCache.map { mapInternal(it) }

    override val value: R
      get() = mapInternal(this@mapStateFlow.value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
      var lastValue: R? = null
      var hasEmittedInitialValue = false

      this@mapStateFlow.collect { value ->
        val newValue = mapInternal(value)
        if (lastValue != newValue || !hasEmittedInitialValue) {
          hasEmittedInitialValue = true
          lastValue = newValue
          collector.emit(newValue)
        }
      }
    }

    private fun mapInternal(value: T): R {
      val localValueMapping = valueMapping
      return if (localValueMapping != null && value === localValueMapping.first) {
        localValueMapping.second
      } else {
        synchronized(lock) {
          val synchronizedValueMapping = valueMapping
          if (synchronizedValueMapping != null && value === synchronizedValueMapping.first) {
            synchronizedValueMapping.second
          } else {
            val result = value to mapper(value)
            valueMapping = result
            result.second
          }
        }
      }
    }
  }
}
