package software.ralf.storymile.runtimemode

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.restart.ApplicationRestarter
import software.ralf.storymile.storage.FileStorage
import software.ralf.storymile.storage.ScopedStorage

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeModeControllerImplTest {
  @Test
  fun `a missing saved mode uses Real without restarting`() = runTestWithScope { scope ->
    val storage = FakeStorage()
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = scope.controller(storage, restarter)

    assertThat(controller.mode()).isEqualTo(RuntimeMode.Real)
    assertThat(storage.preferences.savedMode).isEqualTo(null)
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `scope startup loads and caches the saved mode without restarting`() =
    runTestWithScope { scope ->
      val storage = FakeStorage("Fake")
      val restarter = FakeApplicationRestarter()
      val controller: RuntimeModeController = scope.controller(storage, restarter)
      runCurrent()
      storage.preferences.readFailure = IllegalStateException("Storage unavailable")

      assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
      assertThat(restarter.restartCount).isEqualTo(0)
    }

  @Test
  fun `concurrent first reads wait for storage instead of returning a temporary default`() =
    runTest {
      val storage = FakeStorage("Fake")
      val readBarrier = CompletableDeferred<Unit>()
      storage.preferences.readBarrier = readBarrier
      val restarter = FakeApplicationRestarter()
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(FakeStorageFactory(storage), backgroundScope, restarter)

      val reads = List(2) { async { controller.mode() } }
      runCurrent()
      reads.forEach { assertThat(it.isCompleted).isFalse() }

      readBarrier.complete(Unit)

      assertThat(reads.awaitAll()).isEqualTo(listOf(RuntimeMode.Fake, RuntimeMode.Fake))
      assertThat(restarter.restartCount).isEqualTo(0)
    }

  @Test
  fun `an unknown saved mode fails instead of guessing a mode`() = runTest {
    val storage = FakeStorage("unknown")
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(FakeStorageFactory(storage), backgroundScope, restarter)

    assertFailure { controller.mode() }.isInstanceOf<IllegalStateException>()
    assertFailure { controller.switchMode(RuntimeMode.Real) }.isInstanceOf<IllegalStateException>()

    assertThat(storage.preferences.savedMode).isEqualTo("unknown")
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `a failed read propagates and can be retried without caching a default`() = runTest {
    val storage = FakeStorage("Fake")
    val failure = IllegalStateException("Storage unavailable")
    storage.preferences.readFailure = failure
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(FakeStorageFactory(storage), backgroundScope, restarter)

    assertFailure { controller.mode() }.isSameInstanceAs(failure)

    storage.preferences.readFailure = null

    assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `selecting the saved mode does not restart before or after restoration`() =
    runTestWithScope { scope ->
      val storage = FakeStorage("Fake")
      val restarter = FakeApplicationRestarter()
      val controller: RuntimeModeController = scope.controller(storage, restarter)

      controller.switchMode(RuntimeMode.Fake)
      assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
      assertThat(restarter.restartCount).isEqualTo(0)
      runCurrent()
      controller.switchMode(RuntimeMode.Fake)

      assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
      assertThat(restarter.restartCount).isEqualTo(0)
    }

  @Test
  fun `changing modes saves the selection for the restarted app`() = runTestWithScope { scope ->
    for (initialMode in RuntimeMode.entries) {
      val selectedMode = RuntimeMode.entries.single { it != initialMode }
      val storage = FakeStorage(initialMode.name)
      val restarter = FakeApplicationRestarter {
        assertThat(storage.preferences.savedMode).isEqualTo(selectedMode.name)
      }
      val controller: RuntimeModeController = scope.controller(storage, restarter)
      runCurrent()

      assertThat(controller.mode()).isEqualTo(initialMode)

      controller.switchMode(selectedMode)
      runCurrent()
      assertThat(controller.mode()).isEqualTo(initialMode)
      assertThat(storage.preferences.savedMode).isEqualTo(selectedMode.name)
      assertThat(restarter.restartCount).isEqualTo(1)

      controller.switchMode(selectedMode)
      assertThat(controller.mode()).isEqualTo(initialMode)
      assertThat(restarter.restartCount).isEqualTo(1)

      val nextController: RuntimeModeController =
        scope.controller(storage, FakeApplicationRestarter())
      assertThat(nextController.mode()).isEqualTo(selectedMode)
    }
  }

  @Test
  fun `choosing Real before restoring Fake leaves Fake active until restart`() =
    runTestWithScope { scope ->
      val storage = FakeStorage("Fake")
      val restarter = FakeApplicationRestarter()
      val controller: RuntimeModeController = scope.controller(storage, restarter)

      controller.switchMode(RuntimeMode.Real)
      runCurrent()
      assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
      assertThat(storage.preferences.savedMode).isEqualTo("Real")
      assertThat(restarter.restartCount).isEqualTo(1)

      val nextController: RuntimeModeController =
        scope.controller(storage, FakeApplicationRestarter())
      assertThat(nextController.mode()).isEqualTo(RuntimeMode.Real)
    }

  @Test
  fun `a switch during restore leaves the restored mode active until restart`() =
    runTestWithScope { scope ->
      val storage = FakeStorage("Real")
      val readBarrier = CompletableDeferred<Unit>()
      storage.preferences.readBarrier = readBarrier
      val restarter = FakeApplicationRestarter()
      val controller: RuntimeModeController = scope.controller(storage, restarter)
      runCurrent()

      val switching = async { controller.switchMode(RuntimeMode.Fake) }
      runCurrent()
      readBarrier.complete(Unit)
      switching.await()
      runCurrent()

      assertThat(controller.mode()).isEqualTo(RuntimeMode.Real)
      assertThat(storage.preferences.savedMode).isEqualTo("Fake")
      assertThat(restarter.restartCount).isEqualTo(1)
    }

  @Test
  fun `a failed write preserves the current mode and does not restart`() =
    runTestWithScope { scope ->
      val storage = FakeStorage("Fake")
      val restarter = FakeApplicationRestarter()
      val controller: RuntimeModeController = scope.controller(storage, restarter)
      runCurrent()
      storage.preferences.writeFailure = IllegalStateException("Storage unavailable")

      assertFailure { controller.switchMode(RuntimeMode.Real) }
        .isInstanceOf<IllegalStateException>()

      assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
      assertThat(storage.preferences.savedMode).isEqualTo("Fake")
      assertThat(restarter.restartCount).isEqualTo(0)
    }

  @Test
  fun `concurrent requests for the same mode restart once`() = runTestWithScope { scope ->
    val storage = FakeStorage("Real")
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = scope.controller(storage, restarter)
    runCurrent()
    val writeBarrier = CompletableDeferred<Unit>()
    storage.preferences.writeBarrier = writeBarrier

    val requests = List(2) { async { controller.switchMode(RuntimeMode.Fake) } }
    runCurrent()
    writeBarrier.complete(Unit)
    requests.awaitAll()

    assertThat(controller.mode()).isEqualTo(RuntimeMode.Real)
    assertThat(storage.preferences.savedMode).isEqualTo("Fake")
    assertThat(restarter.restartCount).isEqualTo(1)
  }

  private fun Scope.controller(
    storage: ScopedStorage,
    restarter: ApplicationRestarter,
  ): RuntimeModeController =
    RuntimeModeControllerImpl(FakeStorageFactory(storage), coroutineScope(), restarter).also {
      register(it)
    }

  private class FakeApplicationRestarter(private val onRestart: () -> Unit = {}) :
    ApplicationRestarter {
    var restartCount = 0
      private set

    override fun restart() {
      onRestart()
      restartCount++
    }
  }

  private class FakeStorageFactory(private val storage: ScopedStorage) : ScopedStorage.Factory {
    override fun create(
      namespace: String,
      coroutineScope: CoroutineScope,
      mustBePersistent: Boolean,
    ): ScopedStorage {
      check(namespace == "runtime-mode")
      check(mustBePersistent) { "The runtime mode must survive an app restart." }
      return storage
    }
  }

  private class FakeStorage(savedMode: String? = null) : ScopedStorage {
    val preferences = FakePreferences(savedMode)

    override val files: FileStorage
      get() = error("Files are not used")

    override val cache: FileStorage
      get() = error("Cache is not used")

    override suspend fun preferences(name: String): DataStore<Preferences> {
      check(name == "runtime_mode")
      return preferences
    }
  }

  private class FakePreferences(savedMode: String?) : DataStore<Preferences> {
    private val modeKey = stringPreferencesKey("runtime_mode")
    private val mutex = Mutex()
    private val preferences =
      MutableStateFlow<Preferences>(
        mutablePreferencesOf()
          .apply {
            if (savedMode != null) {
              this[modeKey] = savedMode
            }
          }
          .toPreferences(),
      )

    var readBarrier: CompletableDeferred<Unit>? = null
    var writeBarrier: CompletableDeferred<Unit>? = null
    var readFailure: Exception? = null
    var writeFailure: Exception? = null

    val savedMode: String?
      get() = preferences.value[modeKey]

    override val data: Flow<Preferences> = flow {
      readBarrier?.await()
      readFailure?.let { throw it }
      emitAll(preferences)
    }

    override suspend fun updateData(
      transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
      writeBarrier?.await()
      writeFailure?.let { throw it }
      transform(preferences.value).toPreferences().also { preferences.value = it }
    }
  }
}
