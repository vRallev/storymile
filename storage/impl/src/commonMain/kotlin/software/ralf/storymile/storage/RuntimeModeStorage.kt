package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.ralf.app.platform.scope.Scope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

internal class RuntimeModeStorage(
  private val persistent: ScopedStorage,
  private val memory: ScopedStorage,
  private val coroutineScope: CoroutineScope,
  private val runtimeModeController: RuntimeModeController,
) : ScopedStorage {
  private val job = checkNotNull(coroutineScope.coroutineContext[Job])
  private val enteredScope = CompletableDeferred<Unit>()
  private val preferencesMutex = Mutex()
  private val stores = mutableMapOf<String, DataStore<Preferences>>()

  override val files: FileStorage = RuntimeModeFileStorage(persistent.files, memory.files)
  override val cache: FileStorage = RuntimeModeFileStorage(persistent.cache, memory.cache)

  override suspend fun preferences(name: String): DataStore<Preferences> = modeOperation { mode ->
    storage(mode).preferences(name)
    preferencesMutex.withLock { stores.getOrPut(name) { RuntimeModeDataStore(name) } }
  }

  override fun onEnterScope(scope: Scope) {
    check(!enteredScope.isCompleted) { "Storage has already entered a scope." }
    persistent.onEnterScope(scope)
    memory.onEnterScope(scope)
    enteredScope.complete(Unit)
  }

  override fun onExitScope() {
    coroutineScope.cancel()
    persistent.onExitScope()
    memory.onExitScope()
  }

  private fun storage(mode: RuntimeMode): Storage =
    when (mode) {
      RuntimeMode.Real -> persistent
      RuntimeMode.Fake -> memory
    }

  private suspend fun <T> operation(block: suspend () -> T): T {
    job.ensureActive()
    val callerContext = currentCoroutineContext()
    callerContext.ensureActive()
    val operation =
      coroutineScope.async(callerContext.minusKey(Job)) {
        runCatching {
          enteredScope.await()
          runtimeModeController.awaitInitialization()
          block()
        }
      }
    return try {
      operation.await().getOrThrow()
    } finally {
      operation.cancel()
    }
  }

  private suspend fun <T> modeOperation(block: suspend (RuntimeMode) -> T): T = operation {
    coroutineScope {
      val mode = runtimeModeController.mode.value
      val operationJob = checkNotNull(currentCoroutineContext()[Job])
      val modeObserver =
        launch(start = CoroutineStart.UNDISPATCHED) {
          runtimeModeController.mode.first { it != mode }
          operationJob.cancel(CancellationException("Runtime mode changed."))
        }
      try {
        block(mode).also { currentCoroutineContext().ensureActive() }
      } finally {
        modeObserver.cancel()
      }
    }
  }

  private inner class RuntimeModeFileStorage(
    private val persistent: FileStorage,
    private val memory: FileStorage,
  ) : FileStorage {
    override fun directory(name: String): FileStorage {
      job.ensureActive()
      return RuntimeModeFileStorage(persistent.directory(name), memory.directory(name))
    }

    override suspend fun read(name: String): ByteArray? = modeOperation { storage(it).read(name) }

    override suspend fun write(name: String, content: ByteArray) = modeOperation {
      storage(it).write(name, content)
    }

    override suspend fun delete(name: String) = modeOperation { storage(it).delete(name) }

    override suspend fun deleteAll() = modeOperation { storage(it).deleteAll() }

    private fun storage(mode: RuntimeMode): FileStorage =
      when (mode) {
        RuntimeMode.Real -> persistent
        RuntimeMode.Fake -> memory
      }
  }

  private inner class RuntimeModeDataStore(private val name: String) : DataStore<Preferences> {
    override val data: Flow<Preferences> = channelFlow {
      operation {
        runtimeModeController.mode.collectLatest { mode ->
          storage(mode).preferences(name).data.collect { send(it) }
        }
      }
    }
      .buffer(0)

    override suspend fun updateData(
      transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = modeOperation { mode ->
      storage(mode).preferences(name).updateData { preferences ->
        transform(preferences).also {
          currentCoroutineContext().ensureActive()
          if (runtimeModeController.mode.value != mode) {
            throw CancellationException("Runtime mode changed.")
          }
        }
      }
    }
  }
}
