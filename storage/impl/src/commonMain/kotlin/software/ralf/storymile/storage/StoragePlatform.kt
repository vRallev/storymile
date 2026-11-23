package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.Path
import software.ralf.storymile.util.ExclusiveResourceOwners

/** Platform storage selected by the application graph. */
abstract class StoragePlatform {
  internal val namespaceOwners = ExclusiveResourceOwners("Storage namespace")

  internal abstract fun createPreferences(
    namespace: String,
    name: String,
    coroutineScope: CoroutineScope,
  ): DataStore<Preferences>

  internal abstract fun databasePath(namespace: String, name: String): String?

  internal open fun directoryPath(
    namespace: String,
    area: FileStorageArea,
    path: String,
  ): Path? = null

  internal abstract suspend fun readFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
  ): ByteArray?

  internal abstract suspend fun writeFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
    content: ByteArray,
  )

  internal abstract suspend fun deleteFile(namespace: String, area: FileStorageArea, name: String)

  internal abstract suspend fun deleteAllFiles(
    namespace: String,
    area: FileStorageArea,
    path: String,
  )
}
