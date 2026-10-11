package software.ralf.storymile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import software.ralf.app.platform.scope.Scope
import software.ralf.storymile.storage.FileStorage
import software.ralf.storymile.storage.ScopedStorage

internal class DeferredStorage(
  private val coroutineScope: CoroutineScope,
  createStorage: suspend () -> ScopedStorage,
) : ScopedStorage {
  private val job = checkNotNull(coroutineScope.coroutineContext[Job])
  private val enteredScope = CompletableDeferred<Scope>()
  private val storage =
    coroutineScope.async(start = CoroutineStart.LAZY) {
      val scope = enteredScope.await()
      createStorage().also { it.onEnterScope(scope) }
    }

  override val files: FileStorage = DeferredFileStorage { storage.await().files }
  override val cache: FileStorage = DeferredFileStorage { storage.await().cache }

  override suspend fun preferences(name: String): DataStore<Preferences> =
    storage.await().preferences(name)

  override fun onEnterScope(scope: Scope) {
    check(enteredScope.complete(scope)) { "Storage has already entered a scope." }
    storage.start()
  }

  override fun onExitScope() {
    // Both backends share this dedicated scope, so cancellation also ends their lifecycle.
    coroutineScope.cancel()
  }

  private inner class DeferredFileStorage(private val delegate: suspend () -> FileStorage) :
    FileStorage {
    override fun directory(name: String): FileStorage {
      job.ensureActive()
      return DeferredFileStorage { delegate().directory(name) }
    }

    override suspend fun read(name: String): ByteArray? = delegate().read(name)

    override suspend fun write(name: String, content: ByteArray) = delegate().write(name, content)

    override suspend fun delete(name: String) = delegate().delete(name)

    override suspend fun deleteAll() = delegate().deleteAll()
  }
}
