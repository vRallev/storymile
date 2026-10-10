package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsException
import kotlin.js.Promise
import kotlin.js.asJsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Stores preferences in browser local storage and files in origin-private storage. */
@OptIn(ExperimentalWasmJsInterop::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class WasmJsStoragePlatform : StoragePlatform() {
  private val files = createBrowserFileStorage()

  internal override fun createPreferences(
    namespace: String,
    name: String,
    coroutineScope: CoroutineScope,
  ): DataStore<Preferences> =
    BrowserPreferencesDataStore(
      PreferenceDataStoreFactory.create(
        storage =
          WebLocalStorage(
            serializer = PreferencesSerializer,
            name = "storymile.storage/$namespace/preferences/$name",
          ),
        scope = coroutineScope,
      ),
    )

  internal override suspend fun readFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
  ): ByteArray? = accessBrowserStorage {
    files.readFile(namespace, area.directoryName, name).await()?.let { content ->
      ByteArray(files.byteLength(content)) { index -> files.readByte(content, index).toByte() }
    }
  }

  internal override suspend fun writeFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
    content: ByteArray,
  ) {
    accessBrowserStorage {
      val bytes = files.createBytes(content.size)
      for (index in content.indices) {
        files.writeByte(bytes, index, content[index].toInt() and 0xff)
      }
      val write = files.writeFile(namespace, area.directoryName, name, bytes)
      write.await { files.cancel(write) }
    }
  }

  internal override suspend fun deleteFile(namespace: String, area: FileStorageArea, name: String) {
    accessBrowserStorage {
      val delete = files.deleteFile(namespace, area.directoryName, name)
      delete.await { files.cancel(delete) }
    }
  }

  internal override suspend fun deleteAllFiles(
    namespace: String,
    area: FileStorageArea,
    path: String,
  ) {
    accessBrowserStorage {
      val delete = files.deleteAllFiles(namespace, area.directoryName, path)
      delete.await { files.cancel(delete) }
    }
  }

  private suspend fun <T> accessBrowserStorage(block: suspend () -> T): T =
    try {
      block()
    } catch (exception: JsException) {
      throw IOException("Browser file storage is unavailable.", exception)
    }

  private suspend fun Promise<JsAny?>.await(onCancellation: (() -> Unit)? = null): JsAny? =
    try {
      awaitResult(onCancellation)
    } catch (exception: CancellationException) {
      // Finish browser work before the scope can release this namespace.
      withContext(NonCancellable) { runCatching { awaitResult() } }
      throw exception
    }

  private suspend fun Promise<JsAny?>.awaitResult(onCancellation: (() -> Unit)? = null): JsAny? =
    suspendCancellableCoroutine { continuation ->
      if (onCancellation != null) {
        continuation.invokeOnCancellation { onCancellation() }
      }
      then(
        onFulfilled = { value ->
          continuation.resume(value)
          null
        },
        onRejected = { error ->
          continuation.resumeWithException(
            IOException("Browser file storage is unavailable.", error.asJsException()),
          )
          null
        },
      )
    }

  private class BrowserPreferencesDataStore(private val delegate: DataStore<Preferences>) :
    DataStore<Preferences> {
    override val data: Flow<Preferences> =
      delegate.data.catch { error ->
        if (error is JsException) {
          throw IOException("Browser preferences are unavailable.", error)
        }
        throw error
      }

    override suspend fun updateData(
      transform: suspend (Preferences) -> Preferences,
    ): Preferences =
      try {
        delegate.updateData(transform)
      } catch (exception: JsException) {
        throw IOException("Browser preferences are unavailable.", exception)
      }
  }
}
