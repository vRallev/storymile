@file:Suppress("unused")

package software.ralf.storymile.logging

import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity
import kotlin.jvm.JvmInline
import kotlinx.coroutines.CoroutineScope

/**
 * Writes tagged messages through the shared logging backend.
 *
 * Message lambdas run only when their log level is enabled. Use [withTag] for an explicit tag, or
 * import [logger] to use the receiver's class name.
 */
@JvmInline
value class Logger private constructor(@PublishedApi internal val tag: String) {
  /** Returns a logger that emits with [tag]. */
  fun withTag(tag: String): Logger = Logger(tag)

  /** Writes a verbose [message] with an optional [throwable]. */
  inline fun v(throwable: Throwable? = null, message: () -> String) {
    kermitLogger.logBlock(
      severity = Severity.Verbose,
      tag = tag,
      throwable = throwable,
      message = message,
    )
  }

  /** Writes a debug [message] with an optional [throwable]. */
  inline fun d(throwable: Throwable? = null, message: () -> String) {
    kermitLogger.logBlock(
      severity = Severity.Debug,
      tag = tag,
      throwable = throwable,
      message = message,
    )
  }

  /** Writes an info [message] with an optional [throwable]. */
  inline fun i(throwable: Throwable? = null, message: () -> String) {
    kermitLogger.logBlock(
      severity = Severity.Info,
      tag = tag,
      throwable = throwable,
      message = message,
    )
  }

  /** Writes a warning [message] with an optional [throwable]. */
  inline fun w(throwable: Throwable? = null, message: () -> String) {
    kermitLogger.logBlock(
      severity = Severity.Warn,
      tag = tag,
      throwable = throwable,
      message = message,
    )
  }

  /** Writes an error [message] with an optional [throwable]. */
  inline fun e(throwable: Throwable? = null, message: () -> String) {
    kermitLogger.logBlock(
      severity = Severity.Error,
      tag = tag,
      throwable = throwable,
      message = message,
    )
  }

  /** Writes an assertion [message] with an optional [throwable]. */
  inline fun a(throwable: Throwable? = null, message: () -> String) {
    kermitLogger.logBlock(
      severity = Severity.Assert,
      tag = tag,
      throwable = throwable,
      message = message,
    )
  }

  /** Creates tagged loggers and provides receiver tags. */
  companion object {
    @PublishedApi internal val kermitLogger = KermitLogger

    /** Returns a logger that emits with [tag]. */
    fun withTag(tag: String): Logger = Logger(tag)

    /**
     * Returns a logger tagged with the receiver's simple class name, or `Anonymous` if unavailable.
     */
    @Suppress("MemberNameEqualsClassName")
    val Any.logger: Logger
      get() = withTag(tag = this::class.simpleName ?: "Anonymous")

    /** Prevents a coroutine scope from replacing the caller's class tag. */
    @Suppress("MemberNameEqualsClassName", "UnusedReceiverParameter")
    @Deprecated(level = DeprecationLevel.ERROR, message = "DO NOT USE")
    val CoroutineScope.logger: Logger
      get() = error("DO NOT USE")
  }
}
