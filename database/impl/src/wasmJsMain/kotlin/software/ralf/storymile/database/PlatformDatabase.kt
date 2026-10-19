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
import kotlin.js.Promise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.await
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
    val databaseWorker = createDatabaseWorker().also { worker = it }
    return builder
      .setDriver(WebWorkerSQLiteDriver(databaseWorker))
      .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
      .setSingleConnectionPool()
      .setQueryCoroutineContext(coroutineContext)
      .build()
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
        worker?.let { shutdownDatabaseWorker(it).await<JsAny?>() }
      }
    } finally {
      worker = null
      closed.complete(Unit)
      if (pendingClose === closed) {
        pendingClose = null
      }
    }
  }

  private companion object {
    var pendingClose: CompletableDeferred<Unit>? = null
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
