package software.ralf.storymile.runtimemode

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.storymile.restart.ApplicationRestarter
import software.ralf.storymile.storage.Storage

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class RuntimeModeControllerImpl(
  @ForRuntimeMode private val storage: Storage,
  private val applicationRestarter: ApplicationRestarter,
) : RuntimeModeController, Scoped {
  private val mutex = Mutex()
  private var loadedMode: RuntimeMode? = null

  override suspend fun mode(): RuntimeMode = mutex.withLock { loadMode() }

  override fun onEnterScope(scope: Scope) {
    scope.launch { mode() }
  }

  override suspend fun switchMode(mode: RuntimeMode) {
    mutex.withLock {
      loadMode()
      val preferences = storage.preferences("runtime_mode")
      val savedMode = preferences.readMode()
      if (savedMode == mode) {
        return
      }

      preferences.edit { it[modeKey] = mode.name }
      applicationRestarter.restart()
    }
  }

  private suspend fun loadMode(): RuntimeMode {
    loadedMode?.let {
      return it
    }
    return storage.preferences("runtime_mode").readMode().also { loadedMode = it }
  }

  private suspend fun DataStore<Preferences>.readMode(): RuntimeMode {
    val savedMode = data.first()[modeKey] ?: return RuntimeMode.Real
    return checkNotNull(RuntimeMode.entries.firstOrNull { it.name == savedMode }) {
      "Unknown runtime mode: $savedMode"
    }
  }

  private companion object {
    val modeKey = stringPreferencesKey("runtime_mode")
  }
}
