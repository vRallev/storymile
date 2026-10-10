package software.ralf.storymile.runtimemode

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
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
import software.ralf.storymile.storage.FileStorage
import software.ralf.storymile.storage.Storage

class RuntimeModeControllerImplTest {
  @Test
  fun `missing and unknown saved modes use real services`() {
    listOf(null, "removed-mode").forEach { savedMode ->
      runTestWithScope { scope ->
        val controller: RuntimeModeController =
          RuntimeModeControllerImpl(InMemoryStorage(savedMode)).also { scope.register(it) }
        runCurrent()
        assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
      }
    }
  }

  @Test
  fun `mode selection survives controller recreation`() = runTest {
    val storage = InMemoryStorage()
    val firstScope = Scope.buildTestScope(this)
    try {
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(storage).also { firstScope.register(it) }
      runCurrent()
      controller.switchMode(RuntimeMode.Fake)
      runCurrent()
      assertThat(storage.modeName).isEqualTo("Fake")
    } finally {
      firstScope.destroy()
    }

    val restoredScope = Scope.buildTestScope(this)
    try {
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(storage).also { restoredScope.register(it) }
      runCurrent()
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
      controller.switchMode(RuntimeMode.Real)
      runCurrent()
      assertThat(storage.modeName).isEqualTo("Real")
    } finally {
      restoredScope.destroy()
    }
  }

  @Test
  fun `fake mode never creates the real service and selection follows mode changes`() =
    runTestWithScope { scope ->
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(InMemoryStorage("Fake")).also { scope.register(it) }
      runCurrent()
      assertThat(
          controller.modeImplementation(
            realImpl = { error("The network client must not be created in fake mode.") },
            fakeImpl = { "fake response" },
          ),
        )
        .isEqualTo("fake response")

      controller.switchMode(RuntimeMode.Real)
      assertThat(
          controller.modeImplementation(
            realImpl = { "real response" },
            fakeImpl = { error("The fake service must not be created in real mode.") },
          ),
        )
        .isEqualTo("real response")

      controller.switchMode(RuntimeMode.Fake)
      assertThat(controller.modeImplementation(realImpl = "real", fakeImpl = "fake"))
        .isEqualTo("fake")
    }

  @Test
  fun `startup selection takes precedence over delayed restoration`() = runTestWithScope { scope ->
    val readGate = CompletableDeferred<Unit>()
    val storage = InMemoryStorage("Fake", readGate)
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(storage).also { scope.register(it) }
    runCurrent()
    assertThat(storage.writes).isEqualTo(emptyList())

    controller.switchMode(RuntimeMode.Real)
    readGate.complete(Unit)
    runCurrent()
    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
    assertThat(storage.writes).isEqualTo(listOf("Real"))
  }

  @Test
  fun `restoration does not overwrite a saved fake mode with the default`() =
    runTestWithScope { scope ->
      val storage = InMemoryStorage("Fake")
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(storage).also { scope.register(it) }
      runCurrent()
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
      assertThat(storage.writes).isEqualTo(listOf("Fake"))
    }

  @Test
  fun `unavailable persistence keeps mode selection usable and recovers`() =
    runTestWithScope { scope ->
      val storage = InMemoryStorage().apply { available = false }
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(storage).also { scope.register(it) }
      runCurrent()
      controller.switchMode(RuntimeMode.Fake)
      runCurrent()
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)

      storage.available = true
      controller.switchMode(RuntimeMode.Real)
      runCurrent()
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
      assertThat(storage.modeName).isEqualTo("Real")
    }

  private class InMemoryStorage(
    initialModeName: String? = null,
    private val readGate: CompletableDeferred<Unit>? = null,
  ) : Storage {
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
