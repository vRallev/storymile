package software.ralf.storymile.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.CoroutineContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsBoolean
import kotlin.js.Promise
import kotlin.js.toBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.w3c.dom.Worker
import software.ralf.storymile.user.UserScope

@OptIn(ExperimentalWasmJsInterop::class)
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class PlatformDatabase : DatabasePlatform {
  private var worker: Worker? = null

  override suspend fun open(
    path: String?,
    coroutineContext: CoroutineContext,
  ): RoomPodcastDatabase {
    pendingClose?.await()
    val builder =
      if (path == null) {
        Room.inMemoryDatabaseBuilder<RoomPodcastDatabase> {
          RoomPodcastDatabaseConstructor.initialize()
        }
      } else {
        Room.databaseBuilder<RoomPodcastDatabase>(path) {
          RoomPodcastDatabaseConstructor.initialize()
        }
      }
    return workerMutex.withLock {
      val databaseWorker = createDatabaseWorker()
      val result =
        builder
          .setDriver(WebWorkerSQLiteDriver(databaseWorker))
          .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
          .setSingleConnectionPool()
          .setQueryCoroutineContext(coroutineContext)
          .build()
      worker = databaseWorker
      if (path != null) persistentWorkers.add(databaseWorker)
      result
    }
  }

  override suspend fun close(database: RoomPodcastDatabase) {
    val closed = CompletableDeferred<Unit>().also { pendingClose = it }
    try {
      try {
        // Drain invalidations before Room's synchronous close blocks the browser event loop.
        database.useReaderConnection {}
      } catch (_: SQLiteException) {
        // A database that failed to open still needs its Room handle closed.
      } finally {
        database.close()
        workerMutex.withLock {
          worker?.let {
            persistentWorkers.remove(it)
            shutdownDatabaseWorker(it).await<JsAny?>()
          }
        }
      }
    } finally {
      worker = null
      closed.complete(Unit)
      if (pendingClose === closed) {
        pendingClose = null
      }
    }
  }

  internal companion object {
    private val workerMutex = Mutex()
    private val persistentWorkers = mutableSetOf<Worker>()
    private var pendingClose: CompletableDeferred<Unit>? = null

    // The worker with the open database owns the browser's exclusive SQLite storage lock.
    suspend fun manageFiles(encodedNamespace: String, delete: Boolean): Boolean =
      workerMutex.withLock {
        val active = persistentWorkers.firstOrNull()
        val worker = active ?: createDatabaseWorker()
        try {
          manageDatabaseFiles(worker, encodedNamespace, delete).await<JsBoolean>().toBoolean()
        } finally {
          if (active == null) worker.terminate()
        }
      }
  }
}

// Kotlin/Wasm requires js() bodies in top-level declarations.
@OptIn(ExperimentalWasmJsInterop::class)
private fun createDatabaseWorker(): Worker =
  js(
    """new Worker(new URL("@storymile/sqlite-worker/worker.js", import.meta.url), {type: "module"})""",
  )

@Suppress("UnusedParameter") // Referenced by the JavaScript body.
@OptIn(ExperimentalWasmJsInterop::class)
private fun shutdownDatabaseWorker(worker: Worker): Promise<JsAny?> =
  js(
    """new Promise((resolve, reject) => {
      const onMessage = (event) => {
        if (event.data.id !== -1) return;
        worker.removeEventListener("message", onMessage);
        worker.removeEventListener("error", onError);
        if (event.data.error) reject(new Error(event.data.error));
        else resolve(null);
      };
      const onError = (event) => { reject(new Error(event.message)); };
      worker.addEventListener("message", onMessage);
      worker.addEventListener("error", onError);
      worker.postMessage({id: -1, data: {cmd: "shutdown"}});
    })""",
  )

@Suppress("UnusedParameter") // Referenced by the JavaScript body.
@OptIn(ExperimentalWasmJsInterop::class)
private fun manageDatabaseFiles(
  worker: Worker,
  encodedNamespace: String,
  deleteFiles: Boolean,
): Promise<JsBoolean> =
  js(
    """new Promise((resolve, reject) => {
      const channel = new MessageChannel();
      const finish = () => {
        worker.removeEventListener("error", onError);
        channel.port1.close();
      };
      const onError = (event) => { finish(); reject(new Error(event.message)); };
      worker.addEventListener("error", onError);
      channel.port1.onmessage = ({data}) => {
        finish();
        if (data.error) reject(new Error(data.error));
        else resolve(data.found);
      };
      worker.postMessage({cmd: "namespaceFiles", encodedNamespace, deleteFiles}, [channel.port2]);
    })""",
  )
