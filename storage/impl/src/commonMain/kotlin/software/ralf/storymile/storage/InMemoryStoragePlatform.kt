package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryStoragePlatform : StoragePlatform() {
  private val preferences =
    MutableStateFlow<Map<Pair<String, String>, InMemoryPreferencesStorage>>(emptyMap())
  private val filesMutex = Mutex()
  private val files = mutableMapOf<Triple<String, FileStorageArea, String>, ByteArray>()

  override fun createPreferences(
    namespace: String,
    name: String,
    coroutineScope: CoroutineScope,
  ): DataStore<Preferences> {
    val key = namespace to name
    val storage =
      preferences
        .updateAndGet { stores ->
          if (key in stores) stores else stores + (key to InMemoryPreferencesStorage())
        }
        .getValue(key)
    return PreferenceDataStoreFactory.create(storage = storage, scope = coroutineScope)
  }

  override suspend fun readFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
  ): ByteArray? = filesMutex.withLock { files[Triple(namespace, area, name)]?.copyOf() }

  override suspend fun writeFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
    content: ByteArray,
  ) {
    filesMutex.withLock { files[Triple(namespace, area, name)] = content.copyOf() }
  }

  override suspend fun deleteFile(namespace: String, area: FileStorageArea, name: String) {
    filesMutex.withLock { files.remove(Triple(namespace, area, name)) }
  }

  override suspend fun deleteAllFiles(namespace: String, area: FileStorageArea, path: String) {
    filesMutex.withLock {
      files.keys.removeAll { key ->
        key.first == namespace &&
          key.second == area &&
          (path.isEmpty() || key.third.startsWith("$path/"))
      }
    }
  }
}
