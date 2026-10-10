package software.ralf.storymile.runtimemode

import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.storymile.logging.Logger.Companion.logger
import software.ralf.storymile.storage.Storage

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class RuntimeModeControllerImpl(
  @ForScope(AppScope::class) private val storage: Storage,
) : RuntimeModeController, Scoped {
  private val modeKey = stringPreferencesKey("mode")
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
        storage.preferences("runtime-mode").data.first()[modeKey]
      } catch (exception: IOException) {
        logger.w(exception) { "Could not restore runtime mode." }
        null
      }
    return RuntimeMode.entries.firstOrNull { it.name == name } ?: RuntimeMode.DEFAULT
  }

  private suspend fun persistMode(mode: RuntimeMode) {
    try {
      storage.preferences("runtime-mode").edit { it[modeKey] = mode.name }
    } catch (exception: IOException) {
      // Keep mode selection available when browser storage or the file system is unavailable.
      logger.w(exception) { "Could not save runtime mode." }
    }
  }
}
