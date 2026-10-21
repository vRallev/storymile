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
import software.ralf.storymile.storage.Storage

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PersistentRuntimeModeStorage(
  factory: Storage.Factory,
  @ForScope(AppScope::class) coroutineScope: CoroutineScope,
) : RuntimeModeStorage {
  private val storage = factory.create("runtime-mode", coroutineScope, mustBePersistent = true)

  override fun read(): String? = runBlocking {
    storage.preferences("runtime_mode").data.first()[modeKey]
  }

  override suspend fun write(mode: String) {
    storage.preferences("runtime_mode").edit { it[modeKey] = mode }
  }

  private companion object {
    val modeKey = stringPreferencesKey("runtime_mode")
  }
}
