package software.ralf.storymile.runtimemode

import androidx.datastore.core.IOException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.storymile.logging.Logger.Companion.logger

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class RuntimeModeControllerImpl(private val store: RuntimeModeStore) :
  RuntimeModeController, Scoped {
  private var modeSelectedInMemory = false

  override val mode: StateFlow<RuntimeMode>
    field = MutableStateFlow(RuntimeMode.DEFAULT)

  override fun onEnterScope(scope: Scope) {
    scope.launch {
      val savedMode = readSavedMode()
      if (!modeSelectedInMemory) {
        mode.value = savedMode
      }
      mode.collect { persistMode(it) }
    }
  }

  override fun switchMode(mode: RuntimeMode) {
    modeSelectedInMemory = true
    this.mode.value = mode
  }

  private suspend fun readSavedMode(): RuntimeMode {
    val name =
      try {
        store.readModeName()
      } catch (exception: IOException) {
        logger.w(exception) { "Could not restore runtime mode." }
        null
      }
    return RuntimeMode.entries.firstOrNull { it.name == name } ?: RuntimeMode.DEFAULT
  }

  private suspend fun persistMode(mode: RuntimeMode) {
    try {
      store.writeModeName(mode.name)
    } catch (exception: IOException) {
      // Keep mode selection available when browser storage or the file system is unavailable.
      logger.w(exception) { "Could not save runtime mode." }
    }
  }
}
