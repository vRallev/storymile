package software.ralf.storymile.runtimemode

import kotlinx.coroutines.flow.StateFlow

/** Controls the data source shared by application services and presenters. */
interface RuntimeModeController {
  /** Active mode for this application graph. Stays unchanged while the application restarts. */
  val mode: StateFlow<RuntimeMode>

  /**
   * Selects [mode] before the application scope starts. Overrides the saved mode without
   * restarting. Throws if the application scope has already started.
   */
  fun setInitialMode(mode: RuntimeMode)

  /**
   * Saves [mode] asynchronously, then restarts the application to apply it. The outgoing graph
   * keeps its active mode. Selecting the current mode does nothing. If saving fails, the app does
   * not restart.
   *
   * Requests wait for initialization. After a successful save, further requests are ignored.
   */
  fun switchMode(mode: RuntimeMode)

  /**
   * Waits for the saved mode to load or an explicit selection before accessing mode-specific data.
   */
  suspend fun awaitInitialization()
}

/** Returns the implementation for the current mode. */
fun <T : Any> RuntimeModeController.modeImplementation(realImpl: T, fakeImpl: T): T =
  modeImplementation(realImpl = { realImpl }, fakeImpl = { fakeImpl })

/**
 * Creates only the selected implementation. Use this overload to avoid opening network clients in
 * fake mode. Call it for each service operation to use the latest mode.
 */
inline fun <T : Any> RuntimeModeController.modeImplementation(
  realImpl: () -> T,
  fakeImpl: () -> T,
): T =
  when (mode.value) {
    RuntimeMode.Real -> realImpl()
    RuntimeMode.Fake -> fakeImpl()
  }
