package software.ralf.storymile.runtimemode

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.storymile.storage.ScopedStorage

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PersistentRuntimeModeStorage(
  factory: ScopedStorage.Factory,
  @ForScope(AppScope::class) coroutineScope: CoroutineScope,
  private val rootScopeProvider: RootScopeProvider,
) : RuntimeModeStorage {
  private val storage = factory.create("runtime-mode", coroutineScope, mustBePersistent = true)

  override fun read(): String? = runBlocking {
    rootScopeProvider.rootScope.register(storage)
    storage.preferences("runtime_mode").data.first()[modeKey]
  }

  override suspend fun write(mode: String) {
    rootScopeProvider.rootScope.register(storage)
    storage.preferences("runtime_mode").edit { it[modeKey] = mode }
  }

  private companion object {
    val modeKey = stringPreferencesKey("runtime_mode")
  }
}
