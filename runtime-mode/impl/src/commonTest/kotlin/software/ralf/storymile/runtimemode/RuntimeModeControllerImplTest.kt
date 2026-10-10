package software.ralf.storymile.runtimemode

import androidx.datastore.core.IOException
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.register
import software.ralf.app.platform.scope.runTestWithScope

class RuntimeModeControllerImplTest {
  @Test
  fun `missing and unknown saved modes use real services`() {
    listOf(null, "removed-mode").forEach { savedMode ->
      runTestWithScope { scope ->
        val controller: RuntimeModeController =
          RuntimeModeControllerImpl(InMemoryStore(savedMode)).also { scope.register(it) }
        runCurrent()
        assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
      }
    }
  }

  @Test
  fun `mode selection survives controller recreation`() = runTest {
    val store = InMemoryStore()
    val firstScope = Scope.buildTestScope(this)
    try {
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(store).also { firstScope.register(it) }
      runCurrent()
      controller.switchMode(RuntimeMode.Fake)
      runCurrent()
      assertThat(store.modeName).isEqualTo("Fake")
    } finally {
      firstScope.destroy()
    }

    val restoredScope = Scope.buildTestScope(this)
    try {
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(store).also { restoredScope.register(it) }
      runCurrent()
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
      controller.switchMode(RuntimeMode.Real)
      runCurrent()
      assertThat(store.modeName).isEqualTo("Real")
    } finally {
      restoredScope.destroy()
    }
  }

  @Test
  fun `fake mode never creates the real service and selection follows mode changes`() =
    runTestWithScope { scope ->
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(InMemoryStore("Fake")).also { scope.register(it) }
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
    val store = InMemoryStore("Fake", readGate)
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(store).also { scope.register(it) }
    runCurrent()
    assertThat(store.writes).isEqualTo(emptyList())

    controller.switchMode(RuntimeMode.Real)
    readGate.complete(Unit)
    runCurrent()
    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
    assertThat(store.writes).isEqualTo(listOf("Real"))
  }

  @Test
  fun `restoration does not overwrite a saved fake mode with the default`() =
    runTestWithScope { scope ->
      val store = InMemoryStore("Fake")
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(store).also { scope.register(it) }
      runCurrent()
      assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
      assertThat(store.writes).isEqualTo(listOf("Fake"))
    }

  @Test
  fun `unavailable persistence keeps mode selection usable`() = runTestWithScope { scope ->
    val controller: RuntimeModeController =
      RuntimeModeControllerImpl(
          object : RuntimeModeStore {
            override suspend fun readModeName(): String? =
              throw IOException("Storage is unavailable.")

            override suspend fun writeModeName(modeName: String) {
              throw IOException("Storage is unavailable.")
            }
          },
        )
        .also { scope.register(it) }
    runCurrent()
    controller.switchMode(RuntimeMode.Fake)
    runCurrent()
    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Fake)
    controller.switchMode(RuntimeMode.Real)
    runCurrent()
    assertThat(controller.mode.value).isEqualTo(RuntimeMode.Real)
  }

  private class InMemoryStore(
    var modeName: String? = null,
    private val readGate: CompletableDeferred<Unit>? = null,
  ) : RuntimeModeStore {
    val writes = mutableListOf<String>()

    override suspend fun readModeName(): String? {
      readGate?.await()
      return modeName
    }

    override suspend fun writeModeName(modeName: String) {
      writes += modeName
      this.modeName = modeName
    }
  }
}
