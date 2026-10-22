package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import okio.Path

/** Platform storage selected by the application graph. */
abstract class StoragePlatform {
  private val namespaceOwners = MutableStateFlow<Map<String, List<Job>>>(emptyMap())

  internal fun claimNamespace(namespace: String, owner: Job): List<Job> {
    return namespaceOwners
      .updateAndGet { namespaces ->
        owner.ensureActive()
        val previous = namespaces[namespace].orEmpty().filterNot { it.isCompleted }
        check(previous.none { it.isActive }) {
          "Storage namespace is already owned by another active scope."
        }
        // Keep canceled owners until cleanup finishes, even if a replacement is canceled too.
        namespaces.filterValues { owners -> owners.any { !it.isCompleted } } +
          (namespace to (previous + owner))
      }
      .getValue(namespace)
      .dropLast(1)
  }

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
