package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Platform storage selected by the application graph. */
abstract class StoragePlatform {
  private val namespacesMutex = Mutex()
  private val namespaceOwners = mutableMapOf<String, Job>()

  internal suspend fun claimNamespace(namespace: String, owner: Job) = namespacesMutex.withLock {
    val previous = namespaceOwners[namespace]
    check(previous == null || !previous.isActive) {
      "Storage namespace is already owned by another active scope."
    }
    // DataStore actors and browser writes must finish cleanup before this namespace is reused.
    previous?.join()
    owner.ensureActive()
    namespaceOwners.entries.removeAll { it.value.isCompleted }
    namespaceOwners[namespace] = owner
  }

  internal abstract fun createPreferences(
    namespace: String,
    name: String,
    coroutineScope: CoroutineScope,
  ): DataStore<Preferences>

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
