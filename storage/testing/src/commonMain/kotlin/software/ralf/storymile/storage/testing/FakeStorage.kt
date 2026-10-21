package software.ralf.storymile.storage.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import software.ralf.storymile.storage.FileStorage
import software.ralf.storymile.storage.Storage

/** Provides file fault controls and a fixed database path. Preferences and cache access fail. */
class FakeStorage(
  initialFiles: Map<String, ByteArray> = emptyMap(),
  private val databasePath: String? = null,
) : Storage {
  private val content = initialFiles.mapValues { it.value.copyOf() }.toMutableMap()

  var readBarrier: CompletableDeferred<Unit>? = null
  var writeBarrier: CompletableDeferred<Unit>? = null
  var readFailure: Exception? = null
  var writeFailure: Exception? = null
  var readCount = 0
    private set

  var writeCount = 0
    private set

  val writeStarted = CompletableDeferred<Unit>()
  val writeCanceled = CompletableDeferred<Unit>()

  override val files: FileStorage = Files("")

  override val cache: FileStorage
    get() = error("Cache is not configured")

  /** Reads a relative path without applying read failures, barriers, or counters. */
  fun fileContent(path: String): ByteArray? = content[path]?.copyOf()

  override suspend fun preferences(name: String): DataStore<Preferences> =
    error("Preferences are not configured")

  override suspend fun databasePath(name: String): String? = databasePath

  private inner class Files(private val path: String) : FileStorage {
    override fun directory(name: String): FileStorage = Files("$path$name/")

    override suspend fun read(name: String): ByteArray? {
      readCount++
      readBarrier?.await()
      readFailure?.let { throw it }
      return content["$path$name"]?.copyOf()
    }

    override suspend fun write(name: String, content: ByteArray) {
      writeStarted.complete(Unit)
      try {
        writeBarrier?.await()
        writeFailure?.let { throw it }
        this@FakeStorage.content["$path$name"] = content.copyOf()
        writeCount++
      } catch (exception: CancellationException) {
        writeCanceled.complete(Unit)
        throw exception
      }
    }

    override suspend fun delete(name: String) {
      content.remove("$path$name")
    }

    override suspend fun deleteAll() {
      content.keys.removeAll { it.startsWith(path) }
    }
  }
}
