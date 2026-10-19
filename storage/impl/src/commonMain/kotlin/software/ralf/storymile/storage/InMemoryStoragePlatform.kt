package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stores data in memory. Each instance starts empty. */
class InMemoryStoragePlatform : StoragePlatform() {
  private val preferences =
    MutableStateFlow<Map<PreferencesKey, InMemoryPreferencesDataStore>>(emptyMap())
  private val filesMutex = Mutex()
  private val files = mutableMapOf<FileKey, ByteArray>()

  override suspend fun databasePath(namespace: String, name: String): String? = null

  override fun createPreferences(
    namespace: String,
    name: String,
    coroutineScope: CoroutineScope,
  ): DataStore<Preferences> {
    val key = PreferencesKey(namespace, name)
    return preferences
      .updateAndGet { stores ->
        if (key in stores) stores else stores + (key to InMemoryPreferencesDataStore())
      }
      .getValue(key)
  }

  override suspend fun readFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
  ): ByteArray? = filesMutex.withLock { files[FileKey(namespace, area, name)]?.copyOf() }

  override suspend fun writeFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
    content: ByteArray,
  ) {
    filesMutex.withLock { files[FileKey(namespace, area, name)] = content.copyOf() }
  }

  override suspend fun deleteFile(namespace: String, area: FileStorageArea, name: String) {
    filesMutex.withLock { files.remove(FileKey(namespace, area, name)) }
  }

  override suspend fun deleteAllFiles(namespace: String, area: FileStorageArea, path: String) {
    filesMutex.withLock {
      files.keys.removeAll { key ->
        key.namespace == namespace &&
          key.area == area &&
          (path.isEmpty() || key.path.startsWith("$path/"))
      }
    }
  }

  private data class FileKey(val namespace: String, val area: FileStorageArea, val path: String)

  private data class PreferencesKey(val namespace: String, val name: String)
}
