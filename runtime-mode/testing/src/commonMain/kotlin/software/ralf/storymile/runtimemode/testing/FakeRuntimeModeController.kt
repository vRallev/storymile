package software.ralf.storymile.runtimemode.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

class FakeRuntimeModeController(initialMode: RuntimeMode = RuntimeMode.Fake) :
  RuntimeModeController {
  override val mode: StateFlow<RuntimeMode>
    field = MutableStateFlow(initialMode)

  override fun setInitialMode(mode: RuntimeMode) {
    this.mode.value = mode
  }

  override fun switchMode(mode: RuntimeMode) {
    this.mode.value = mode
  }

  override suspend fun awaitInitialization() = Unit
}
