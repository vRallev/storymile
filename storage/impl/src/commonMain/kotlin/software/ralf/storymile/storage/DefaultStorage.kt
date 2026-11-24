package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import okio.Path
import software.ralf.storymile.util.runChildOperation

/**
 * Scope-owned implementation used by [Storage.Factory].
 *
 * Construction claims the namespace. File and preference operations wait for canceled owners to
 * finish, including DataStore cleanup.
 *
 * UTF-8 hex encoding preserves namespace and name identity on case-insensitive filesystems.
 * Directory handles encode single path segments and share the same owner. Durable files and cache
 * files use separate roots. One mutex serializes file operations across handles and both areas, so
 * an earlier write cannot finish after a directory is cleared.
 *
 * Named DataStores are cached. Operations retain the caller's transaction context. Canceling the
 * caller or [coroutineScope] cancels the operation. Failures reach the caller. Scope cancellation
 * preserves saved data.
 */
class DefaultStorage(
  namespace: String,
  private val coroutineScope: CoroutineScope,
  private val platform: StoragePlatform,
) : Storage {
  private val encodedNamespace = encodeNamespace(namespace)
  private val previousOwners =
    platform.namespaceOwners.claim(
      key = encodedNamespace,
      owner = coroutineScope.coroutineContext.job,
    )
  private val filesMutex = Mutex()
  private val stores = MutableStateFlow<Map<String, DataStore<Preferences>>>(emptyMap())

  override val files: FileStorage = DirectoryFileStorage(FileStorageArea.Files, "")
  override val cache: FileStorage = DirectoryFileStorage(FileStorageArea.Cache, "")

  override fun preferences(name: String): DataStore<Preferences> {
    coroutineScope.ensureActive()
    require(name.matches(namePattern)) {
      "Preferences name must contain only letters, digits, underscores, and hyphens."
    }
    return stores
      .updateAndGet { stores ->
        if (name in stores) stores else stores + (name to GuardedDataStore(name))
      }
      .getValue(name)
  }

  override fun databasePath(name: String): String? {
    coroutineScope.ensureActive()
    require(name.matches(namePattern)) {
      "Database name must contain only letters, digits, underscores, and hyphens."
    }
    return platform.databasePath(namespace = encodedNamespace, name = name.encodeUtf8().hex())
  }

  private suspend fun <T> operation(block: suspend () -> T): T = coroutineScope.runChildOperation {
    previousOwners.forEach { it.join() }
    block()
  }

  private fun requireFileName(name: String) {
    require(name.isNotBlank() && name != "." && name != "..") {
      "File name must not be blank or dot."
    }
    require(name.none { it == '/' || it == '\\' || it == '\u0000' || it == ':' }) {
      "File name must not contain path separators, null characters, or a drive prefix."
    }
  }

  private companion object {
    val namePattern = Regex("[A-Za-z0-9_-]+")

    fun encodeNamespace(namespace: String): String {
      require(namespace.matches(namePattern)) {
        "Storage namespace must contain only letters, digits, underscores, and hyphens."
      }
      return namespace.encodeUtf8().hex()
    }
  }

  @SingleIn(AppScope::class)
  @ContributesBinding(AppScope::class, binding = binding<Storage.Factory>())
  class Factory(
    private val platform: StoragePlatform,
  ) : Storage.Factory {
    override suspend fun hasNamespace(namespace: String): Boolean = coroutineScope {
      platform.hasNamespace(claimNamespace(namespace))
    }

    override suspend fun copyPreferences(sourceNamespace: String, targetNamespace: String) {
      require(sourceNamespace != targetNamespace) { "Choose a different destination namespace." }
      coroutineScope {
        platform.copyPreferences(
          sourceNamespace = claimNamespace(sourceNamespace),
          targetNamespace = claimNamespace(targetNamespace),
        )
      }
    }

    override suspend fun deleteNamespace(namespace: String) = coroutineScope {
      platform.deleteNamespace(claimNamespace(namespace))
    }

    private suspend fun CoroutineScope.claimNamespace(namespace: String): String {
      val encoded = encodeNamespace(namespace)
      platform.namespaceOwners.claim(key = encoded, owner = coroutineContext.job).forEach {
        it.join()
      }
      return encoded
    }

    override fun create(
      namespace: String,
      coroutineScope: CoroutineScope,
      mustBePersistent: Boolean,
    ): Storage =
      DefaultStorage(namespace = namespace, coroutineScope = coroutineScope, platform = platform)
  }

  private inner class DirectoryFileStorage(
    private val area: FileStorageArea,
    private val relativePath: String,
  ) : FileStorage {
    override val path: Path? by lazy {
      platform.directoryPath(namespace = encodedNamespace, area = area, path = relativePath)
    }

    override fun directory(name: String): FileStorage {
      coroutineScope.ensureActive()
      return DirectoryFileStorage(area, resolve(name))
    }

    override suspend fun read(name: String): ByteArray? = operation {
      filesMutex.withLock { platform.readFile(encodedNamespace, area, resolve(name)) }
    }

    override suspend fun write(name: String, content: ByteArray) = operation {
      filesMutex.withLock { platform.writeFile(encodedNamespace, area, resolve(name), content) }
    }

    override suspend fun delete(name: String) = operation {
      filesMutex.withLock { platform.deleteFile(encodedNamespace, area, resolve(name)) }
    }

    override suspend fun deleteAll() = operation {
      filesMutex.withLock { platform.deleteAllFiles(encodedNamespace, area, relativePath) }
    }

    private fun resolve(name: String): String {
      requireFileName(name)
      val encodedName = name.encodeUtf8().hex()
      return if (relativePath.isEmpty()) encodedName else "$relativePath/$encodedName"
    }
  }

  private inner class GuardedDataStore(name: String) : DataStore<Preferences> {
    private val delegate by lazy {
      platform.createPreferences(encodedNamespace, name.encodeUtf8().hex(), coroutineScope)
    }

    override val data: Flow<Preferences> = channelFlow {
      operation { delegate.data.collect { send(it) } }
    }

    override suspend fun updateData(
      transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = operation { delegate.updateData(transform) }
  }
}
