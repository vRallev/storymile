package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

class RuntimeModeStorageTest {
  @Test
  fun `persistent storage bypasses runtime mode loading`() = runTestWithScope { scope ->
    val platform = InMemoryStoragePlatform()
    val factory: ScopedStorage.Factory =
      DefaultStorage.Factory(platform, lazy { error("Runtime mode must not be checked") })
    val owner = scope.coroutineScope()
    val storage: ScopedStorage = factory.create("runtime-mode", owner, mustBePersistent = true)
    scope.register(storage)
    val key = stringPreferencesKey("runtime_mode")
    storage.preferences("settings").edit { it[key] = "Fake" }
    storage.files.write("state", byteArrayOf(1))
    storage.cache.write("state", byteArrayOf(2))
    owner.cancel()

    val reopened: Storage =
      DefaultStorage.create("runtime-mode", scope.coroutineScope(), platform).also {
        scope.register(it)
      }
    assertThat(reopened.preferences("settings").data.first()[key]).isEqualTo("Fake")
    assertThat(reopened.files.read("state")).isNotNull().isEqualTo(byteArrayOf(1))
    assertThat(reopened.cache.read("state")).isNotNull().isEqualTo(byteArrayOf(2))
  }

  @Test
  fun `creation waits for the saved mode and keeps fake data off persistent storage`() =
    runTestWithScope { scope ->
      val realPlatform = InMemoryStoragePlatform()
      val real: Storage = scope.storage("app", realPlatform)
      val key = stringPreferencesKey("theme")
      real.preferences("settings").edit { it[key] = "real" }
      real.files.write("chapter", byteArrayOf(1))
      real.cache.write("cover", byteArrayOf(2))
      val mode = CompletableDeferred<RuntimeMode>()
      val factory: ScopedStorage.Factory =
        DefaultStorage.Factory(realPlatform, lazy { FakeRuntimeModeController(mode) })
      val owner = scope.coroutineScope()
      val creating = async { factory.create("app", owner) }
      runCurrent()

      assertThat(creating.isCompleted).isFalse()
      mode.complete(RuntimeMode.Fake)
      val storage: Storage = creating.await().also { scope.register(it) }

      assertThat(storage.preferences("settings").data.first()[key]).isNull()
      assertThat(storage.files.read("chapter")).isNull()
      assertThat(storage.cache.read("cover")).isNull()
      assertThat(storage.databasePath("podcasts")).isNull()
      storage.preferences("settings").edit { it[key] = "fake" }
      storage.files.write("chapter", byteArrayOf(3))
      storage.cache.write("cover", byteArrayOf(4))

      assertThat(storage.preferences("settings").data.first()[key]).isEqualTo("fake")
      assertThat(storage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(3))
      assertThat(storage.cache.read("cover")).isNotNull().isEqualTo(byteArrayOf(4))
      assertThat(real.preferences("settings").data.first()[key]).isEqualTo("real")
      assertThat(real.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(real.cache.read("cover")).isNotNull().isEqualTo(byteArrayOf(2))
    }

  @Test
  fun `failed creation can retry without falling back to persistent storage`() =
    runTestWithScope { scope ->
      val mode =
        CompletableDeferred<RuntimeMode>().also {
          it.completeExceptionally(IllegalStateException("Mode unavailable"))
        }
      val controller = FakeRuntimeModeController(mode)
      val factory: ScopedStorage.Factory =
        DefaultStorage.Factory(InMemoryStoragePlatform(), lazy { controller })
      val owner = scope.coroutineScope()

      assertFailure { factory.create("app", owner) }
        .isInstanceOf<IllegalStateException>()
        .hasMessage("Mode unavailable")

      controller.selectedMode = CompletableDeferred(RuntimeMode.Fake)
      val storage: Storage = factory.create("app", owner).also { scope.register(it) }
      storage.files.write("chapter", byteArrayOf(1))
      assertThat(storage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
    }

  @Test
  fun `canceling creation allows retry with the same owner and namespace`() =
    runTestWithScope { scope ->
      val mode = CompletableDeferred<RuntimeMode>()
      val factory: ScopedStorage.Factory =
        DefaultStorage.Factory(InMemoryStoragePlatform(), lazy { FakeRuntimeModeController(mode) })
      val owner = scope.coroutineScope()
      val creating = async { factory.create("app", owner) }
      runCurrent()
      assertThat(creating.isCompleted).isFalse()

      creating.cancelAndJoin()
      mode.complete(RuntimeMode.Fake)
      val storage: Storage = factory.create("app", owner).also { scope.register(it) }

      storage.files.write("chapter", byteArrayOf(1))
      assertThat(storage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
    }

  @Test
  fun `scope exit cancels creation while the saved mode is loading`() = runTestWithScope { scope ->
    val mode = CompletableDeferred<RuntimeMode>()
    val factory: ScopedStorage.Factory =
      DefaultStorage.Factory(InMemoryStoragePlatform(), lazy { FakeRuntimeModeController(mode) })
    val owner = scope.coroutineScope()
    val creating = async {
      assertFailure { factory.create("app", owner) }.isInstanceOf<CancellationException>()
    }
    runCurrent()
    assertThat(creating.isCompleted).isFalse()

    scope.destroy()

    creating.await()
  }

  private class FakeRuntimeModeController(var selectedMode: CompletableDeferred<RuntimeMode>) :
    RuntimeModeController {
    override suspend fun mode(): RuntimeMode = selectedMode.await()

    override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
  }
}
