package software.ralf.storymile.runtimemode

import kotlinx.coroutines.flow.StateFlow

/** Controls the data source shared by application services and presenters. */
interface RuntimeModeController {
  /** Current mode. Observe this flow to reset state when the data source changes. */
  val mode: StateFlow<RuntimeMode>

  /** Selects [mode]. The application scope saves the selection asynchronously. */
  fun switchMode(mode: RuntimeMode)
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
