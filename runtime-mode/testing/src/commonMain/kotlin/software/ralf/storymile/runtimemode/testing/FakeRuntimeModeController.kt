package software.ralf.storymile.runtimemode.testing

import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

/** Keeps the session mode fixed and records switch requests without persistence or restarts. */
class FakeRuntimeModeController(private val selectedMode: RuntimeMode = RuntimeMode.Fake) :
  RuntimeModeController {
  /** Throws on each mode read until cleared. */
  var modeFailure: Exception? = null

  /** The last switch request, or `null` before the first request. */
  var requestedMode: RuntimeMode? = null
    private set

  override val mode: RuntimeMode
    get() {
      modeFailure?.let { throw it }
      return selectedMode
    }

  override suspend fun switchMode(mode: RuntimeMode) {
    requestedMode = mode
  }
}
