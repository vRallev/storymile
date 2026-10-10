package software.ralf.storymile.runtimemode

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import software.ralf.storymile.storage.Storage

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DataStoreRuntimeModeStore(
  @ForScope(AppScope::class) private val storage: Storage,
) : RuntimeModeStore {
  private val modeKey = stringPreferencesKey("mode")

  override suspend fun readModeName(): String? =
    storage.preferences("runtime-mode").data.first()[modeKey]

  override suspend fun writeModeName(modeName: String) {
    storage.preferences("runtime-mode").edit { it[modeKey] = modeName }
  }
}
