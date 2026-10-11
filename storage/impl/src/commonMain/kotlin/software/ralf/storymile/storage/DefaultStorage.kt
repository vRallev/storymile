package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import software.ralf.app.platform.scope.Scope

/**
 * Scope-owned implementation used by [ScopedStorage.Factory].
 *
 * The graph can construct storage before its App Platform scope exists. The first operation waits
 * for [onEnterScope], then claims the namespace once. A claim rejects an active owner and waits for
 * a canceled owner's work to finish, including DataStore cleanup.
 *
 * UTF-8 hex encoding preserves namespace and name identity on case-insensitive filesystems.
 * Directory handles encode single path segments and share the same owner. Durable files and cache
 * files use separate roots. One mutex serializes file operations across handles and both areas, so
 * an earlier write cannot finish after a directory is cleared.
 *
 * Named DataStores are cached. Their guards route updates and collection through the owned
 * [coroutineScope], so retained handles stop working after exit. Each operation preserves the
 * caller's context except its [Job], which keeps DataStore's transaction context intact. Caller
 * cancellation cancels that operation.
 *
 * Child coroutines return [Result] so failures reach the caller without canceling the injected
 * scope's regular [Job]. Exit cancels the dedicated scope, stopping actors and operations while
 * preserving saved data.
 */
@AssistedInject
class DefaultStorage(
  @Assisted namespace: String,
  @Assisted private val coroutineScope: CoroutineScope,
  private val platform: StoragePlatform,
) : ScopedStorage {
  init {
    require(namespace.matches(namePattern)) {
      "Storage namespace must contain only letters, digits, underscores, and hyphens."
    }
  }

  private val encodedNamespace = namespace.encodeUtf8().hex()
  private val job = checkNotNull(coroutineScope.coroutineContext[Job])
  private val enteredScope = CompletableDeferred<Unit>()
  private val initialized =
    coroutineScope.async(start = CoroutineStart.LAZY) {
      runCatching {
        enteredScope.await()
        platform.claimNamespace(encodedNamespace, job)
      }
    }
  private val preferencesMutex = Mutex()
  private val filesMutex = Mutex()
  private val stores = mutableMapOf<String, DataStore<Preferences>>()

  override val files: FileStorage = DirectoryFileStorage(FileStorageArea.Files, "")
  override val cache: FileStorage = DirectoryFileStorage(FileStorageArea.Cache, "")

  override suspend fun preferences(name: String): DataStore<Preferences> = operation {
    require(name.matches(namePattern)) {
      "Preferences name must contain only letters, digits, underscores, and hyphens."
    }
    preferencesMutex.withLock {
      stores.getOrPut(name) {
        GuardedDataStore(
          platform.createPreferences(encodedNamespace, name.encodeUtf8().hex(), coroutineScope),
        )
      }
    }
  }

  override fun onEnterScope(scope: Scope) {
    check(enteredScope.complete(Unit)) { "Storage has already entered a scope." }
  }

  override fun onExitScope() {
    coroutineScope.cancel()
  }

  private fun checkActive() {
    job.ensureActive()
  }

  private suspend fun <T> operation(block: suspend () -> T): T {
    checkActive()
    val callerContext = currentCoroutineContext()
    callerContext.ensureActive()
    val operation =
      coroutineScope.async(callerContext.minusKey(Job)) {
        runCatching {
          initialized.await().getOrThrow()
          block()
        }
      }
    return try {
      operation.await().getOrThrow()
    } finally {
      operation.cancel()
    }
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
  }

  @AssistedFactory
  @ContributesBinding(AppScope::class, binding = binding<ScopedStorage.Factory>())
  interface Factory : ScopedStorage.Factory {
    override fun create(namespace: String, coroutineScope: CoroutineScope): DefaultStorage
  }

  private inner class DirectoryFileStorage(
    private val area: FileStorageArea,
    private val path: String,
  ) : FileStorage {
    override fun directory(name: String): FileStorage {
      checkActive()
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
      filesMutex.withLock { platform.deleteAllFiles(encodedNamespace, area, path) }
    }

    private fun resolve(name: String): String {
      requireFileName(name)
      val encodedName = name.encodeUtf8().hex()
      return if (path.isEmpty()) encodedName else "$path/$encodedName"
    }
  }

  private inner class GuardedDataStore(private val delegate: DataStore<Preferences>) :
    DataStore<Preferences> {
    override val data: Flow<Preferences> = channelFlow {
      operation { delegate.data.collect { send(it) } }
    }

    override suspend fun updateData(
      transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = operation { delegate.updateData(transform) }
  }
}
