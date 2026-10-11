@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile.runtimemode

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.register
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.restart.ApplicationRestarter
import software.ralf.storymile.storage.FileStorage
import software.ralf.storymile.storage.ScopedStorage

class RuntimeModeControllerImplTest {
  @Test
  fun `scope exit cancels pending mode initialization`() = runTest {
    val scope = Scope.buildTestScope(this)
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(
          InMemoryStorage(readGate = CompletableDeferred()),
          restarter { error("Initialization must not restart the application.") },
        )
        .also { scope.register(it) }
    val initializing = async {
      assertFailsWith<CancellationException> { controller.awaitInitialization() }
    }
    runCurrent()
    assertFalse(initializing.isCompleted)

    scope.destroy()

    initializing.await()
  }

  @Test
  fun `saved modes restore without writing or restarting`() {
    listOf(null to RuntimeMode.Real, "removed-mode" to RuntimeMode.Real, "Fake" to RuntimeMode.Fake)
      .forEach { (savedMode, expectedMode) ->
        runTestWithScope { scope ->
          val storage = InMemoryStorage(savedMode)
          val controller: RuntimeModeController =
            RuntimeModeControllerImpl(
                storage,
                restarter { error("Restoration must not restart the application.") },
              )
              .also { scope.register(it) }
          controller.awaitInitialization()
          assertThat(controller.mode.value).isEqualTo(expectedMode)
          assertThat(storage.writes).isEqualTo(emptyList())
        }
      }
  }

  @Test
  fun `enabling and disabling fake mode survive application recreation`() = runTest {
    val storage = InMemoryStorage()
    var restartCount = 0
    listOf(RuntimeMode.Real to RuntimeMode.Fake, RuntimeMode.Fake to RuntimeMode.Real).forEach {
      (startingMode, selectedMode) ->
      val scope = Scope.buildTestScope(this)
      try {
        val controller: RuntimeModeController =
          RuntimeModeControllerImpl(
              storage,
              restarter {
                assertThat(storage.modeName).isEqualTo(selectedMode.name)
                restartCount += 1
              },
            )
            .also { scope.register(it) }
        controller.awaitInitialization()
        assertThat(controller.mode.value).isEqualTo(startingMode)

        controller.switchMode(selectedMode)
        runCurrent()

        assertThat(controller.mode.value).isEqualTo(startingMode)
      } finally {
        scope.destroy()
      }
    }
    assertThat(restartCount).isEqualTo(2)

    val restoredScope = Scope.buildTestScope(this)
    try {
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(
            storage,
            restarter { error("Restoration must not restart the application.") },
          )
          .also { restoredScope.register(it) }
      controller.awaitInitialization()
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
    } finally {
      restoredScope.destroy()
    }
  }

  @Test
  fun `restart waits for the selected mode to be saved and accepts only one change`() =
    runTestWithScope { scope ->
      val writeGate = CompletableDeferred<Unit>()
      val storage = InMemoryStorage(writeGate = writeGate)
      var restartCount = 0
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(
            storage,
            restarter {
              assertThat(storage.modeName).isEqualTo("Fake")
              restartCount += 1
            },
          )
          .also { scope.register(it) }
      controller.awaitInitialization()

      controller.switchMode(RuntimeMode.Real)
      runCurrent()
      assertThat(storage.writes).isEqualTo(emptyList())
      controller.switchMode(RuntimeMode.Fake)
      runCurrent()
      repeat(3) { controller.switchMode(RuntimeMode.Fake) }
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
      assertThat(restartCount).isEqualTo(0)

      writeGate.complete(Unit)
      runCurrent()
      controller.switchMode(RuntimeMode.Real)
      runCurrent()

      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
      assertThat(storage.writes).isEqualTo(listOf("Fake"))
      assertThat(restartCount).isEqualTo(1)
    }

  @Test
  fun `requests wait for restoration before comparing the selected mode`() =
    runTestWithScope { scope ->
      val readGate = CompletableDeferred<Unit>()
      val storage = InMemoryStorage("Fake", readGate = readGate)
      var restartCount = 0
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(storage, restarter { restartCount += 1 }).also {
          scope.register(it)
        }
      controller.switchMode(RuntimeMode.Real)
      val initializing = async { controller.awaitInitialization() }
      runCurrent()
      assertFalse(initializing.isCompleted)
      assertThat(storage.writes).isEqualTo(emptyList())
      assertThat(restartCount).isEqualTo(0)

      readGate.complete(Unit)
      initializing.await()
      runCurrent()

      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
      assertThat(storage.writes).isEqualTo(listOf("Real"))
      assertThat(restartCount).isEqualTo(1)
    }

  @Test
  fun `only the latest request waiting for restoration is applied`() = runTestWithScope { scope ->
    val readGate = CompletableDeferred<Unit>()
    val storage = InMemoryStorage("Real", readGate = readGate)
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(
          storage,
          restarter { error("The final request selects the current mode.") },
        )
        .also { scope.register(it) }
    controller.switchMode(RuntimeMode.Fake)
    controller.switchMode(RuntimeMode.Real)
    readGate.complete(Unit)
    runCurrent()

    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
    assertThat(storage.writes).isEqualTo(emptyList())
  }

  @Test
  fun `startup selection is available before saving and overrides restoration without restarting`() =
    runTestWithScope { scope ->
      val writeGate = CompletableDeferred<Unit>()
      val storage = InMemoryStorage("Real", readGate = CompletableDeferred(), writeGate = writeGate)
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(
            storage,
            restarter { error("A startup selection must not restart the application.") },
          )
          .also {
            it.setInitialMode(RuntimeMode.Fake)
            scope.register(it)
          }
      controller.awaitInitialization()
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
      runCurrent()
      assertThat(storage.writes).isEqualTo(emptyList())
      assertFailsWith<IllegalStateException> { controller.setInitialMode(RuntimeMode.Real) }

      writeGate.complete(Unit)
      runCurrent()

      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
      assertThat(storage.writes).isEqualTo(listOf("Fake"))
    }

  @Test
  fun `startup selection stays available when persistence fails`() = runTestWithScope { scope ->
    val storage = InMemoryStorage().apply { available = false }
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(
          storage,
          restarter { error("A startup selection must not restart the application.") },
        )
        .also {
          it.setInitialMode(RuntimeMode.Fake)
          scope.register(it)
        }
    runCurrent()
    controller.awaitInitialization()

    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
    assertThat(storage.writes).isEqualTo(emptyList())
  }

  @Test
  fun `failed saves keep the running mode and can be retried`() = runTestWithScope { scope ->
    val storage = InMemoryStorage()
    var restartCount = 0
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(storage, restarter { restartCount += 1 }).also {
        scope.register(it)
      }
    controller.awaitInitialization()
    storage.available = false

    controller.switchMode(RuntimeMode.Fake)
    runCurrent()

    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
    assertThat(storage.writes).isEqualTo(emptyList())
    assertThat(restartCount).isEqualTo(0)

    storage.available = true
    controller.switchMode(RuntimeMode.Fake)
    runCurrent()

    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
    assertThat(storage.writes).isEqualTo(listOf("Fake"))
    assertThat(restartCount).isEqualTo(1)
  }

  @Test
  fun `scope exit cancels a pending save without restarting`() = runTest {
    val scope = Scope.buildTestScope(this)
    val storage = InMemoryStorage(writeGate = CompletableDeferred())
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(
          storage,
          restarter { error("A cancelled save must not restart the application.") },
        )
        .also { scope.register(it) }
    controller.awaitInitialization()
    controller.switchMode(RuntimeMode.Fake)
    runCurrent()

    scope.destroy()
    runCurrent()
    controller.switchMode(RuntimeMode.Fake)
    runCurrent()

    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
    assertThat(storage.writes).isEqualTo(emptyList())
  }

  @Test
  fun `outgoing services stay in fake mode while restarting into real mode`() =
    runTestWithScope { scope ->
      val storage = InMemoryStorage("Fake")
      var restartCount = 0
      lateinit var controller: RuntimeModeController
      controller =
        RuntimeModeControllerImpl(
            storage,
            restarter {
              assertThat(storage.modeName).isEqualTo("Real")
              assertThat(
                  controller.modeImplementation(
                    realImpl = {
                      error("Outgoing fake services must not create a network client.")
                    },
                    fakeImpl = { "fake response" },
                  ),
                )
                .isEqualTo("fake response")
              restartCount += 1
            },
          )
          .also { scope.register(it) }
      controller.awaitInitialization()
      assertThat(
          controller.modeImplementation(
            realImpl = { error("The network client must not be created in fake mode.") },
            fakeImpl = { "fake response" },
          ),
        )
        .isEqualTo("fake response")

      controller.switchMode(RuntimeMode.Real)
      runCurrent()

      assertThat(restartCount).isEqualTo(1)
      assertThat(
          controller.modeImplementation(
            realImpl = { error("Outgoing fake services must not create a network client.") },
            fakeImpl = { "fake response" },
          ),
        )
        .isEqualTo("fake response")
    }

  private fun restarter(onRestart: () -> Unit): ApplicationRestarter =
    object : ApplicationRestarter {
      override fun restart() {
        onRestart()
      }
    }

  private class InMemoryStorage(
    initialModeName: String? = null,
    private val readGate: CompletableDeferred<Unit>? = null,
    private val writeGate: CompletableDeferred<Unit>? = null,
  ) : ScopedStorage {
    private val modeKey = stringPreferencesKey("mode")
    private val mutex = Mutex()
    private val preferences =
      MutableStateFlow<Preferences>(
        emptyPreferences()
          .toMutablePreferences()
          .apply {
            if (initialModeName != null) {
              this[modeKey] = initialModeName
            }
          }
          .toPreferences(),
      )

    private val dataStore =
      object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
          readGate?.await()
          checkAvailability()
          emitAll(preferences)
        }

        override suspend fun updateData(
          transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = mutex.withLock {
          writeGate?.await()
          checkAvailability()
          transform(preferences.value).also {
            preferences.value = it
            writes += it[modeKey]
          }
        }
      }

    var available = true
    val writes = mutableListOf<String?>()
    val modeName: String?
      get() = preferences.value[modeKey]

    override val files: FileStorage
      get() = error("File storage is not used by runtime mode.")

    override val cache: FileStorage
      get() = error("Cache storage is not used by runtime mode.")

    override suspend fun preferences(name: String): DataStore<Preferences> {
      require(name == "runtime-mode")
      return dataStore
    }

    private fun checkAvailability() {
      if (!available) {
        throw IOException("Storage is unavailable.")
      }
    }
  }
}
