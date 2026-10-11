package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeModeStorageTest {
  private val key = stringPreferencesKey("value")

  @Test
  fun `fake app and user data never read or change persistent data`() = runTestWithScope { scope ->
    val persistent = InMemoryStoragePlatform()
    val fake = RuntimeModeStoragePlatform(persistent, ModeController(RuntimeMode.Fake))
    for (namespace in listOf("app", "user-guest")) {
      val realStorage: Storage = scope.storage(namespace, persistent)
      write(realStorage, "real")
      val fakeStorage: Storage = scope.storage(namespace, fake)
      assertEmpty(fakeStorage)
      write(fakeStorage, namespace)
      assertContents(fakeStorage, namespace)

      fakeStorage.files.delete("value")
      fakeStorage.cache.deleteAll()
      fakeStorage.files.write("value", byteArrayOf(1))
      fakeStorage.cache.write("value", byteArrayOf(1))
      fakeStorage.files.deleteAll()
      fakeStorage.cache.delete("value")
      fakeStorage.preferences("settings").edit { it.clear() }
      assertEmpty(fakeStorage)
      assertContents(realStorage, "real")
    }
  }

  @Test
  fun `new fake sessions start empty while real sessions reuse persistent data`() =
    runTestWithScope { scope ->
      val persistent = InMemoryStoragePlatform()
      val real: Storage =
        scope.storage(
          "app",
          RuntimeModeStoragePlatform(persistent, ModeController(RuntimeMode.Real)),
        )
      write(real, "real")
      val fake: Storage =
        scope.storage(
          "app",
          RuntimeModeStoragePlatform(persistent, ModeController(RuntimeMode.Fake)),
        )
      write(fake, "fake")

      val nextFake: Storage =
        scope.storage(
          "app",
          RuntimeModeStoragePlatform(persistent, ModeController(RuntimeMode.Fake)),
        )
      assertEmpty(nextFake)
      val nextReal: Storage =
        scope.storage(
          "app",
          RuntimeModeStoragePlatform(persistent, ModeController(RuntimeMode.Real)),
        )
      assertContents(nextReal, "real")
    }

  @Test
  fun `preferences files and caches wait for the saved mode before accessing data`() =
    runTestWithScope { scope ->
      val persistent = InMemoryStoragePlatform()
      write(scope.storage("app", persistent), "real")
      val controller = ModeController()
      val storage: Storage =
        scope.storage("app", RuntimeModeStoragePlatform(persistent, controller))
      val preferences = async { storage.preferences("settings").data.first()[key] }
      val file = async { storage.files.read("value") }
      val cache = async { storage.cache.read("value") }
      runCurrent()
      listOf(preferences, file, cache).forEach { assertThat(it.isCompleted).isFalse() }

      controller.loadedMode.complete(RuntimeMode.Fake)

      assertThat(preferences.await()).isNull()
      assertThat(file.await()).isNull()
      assertThat(cache.await()).isNull()
    }

  @Test
  fun `mode loading failures never fall back to persistent storage`() = runTestWithScope { scope ->
    val persistent = InMemoryStoragePlatform()
    val real: Storage = scope.storage("app", persistent)
    write(real, "real")
    val controller = ModeController()
    controller.loadedMode.completeExceptionally(IllegalStateException("Mode unavailable"))
    val storage: Storage = scope.storage("app", RuntimeModeStoragePlatform(persistent, controller))

    assertFailure { storage.preferences("settings") }.isInstanceOf<IllegalStateException>()
    assertFailure { storage.files.deleteAll() }.isInstanceOf<IllegalStateException>()
    assertFailure { storage.cache.write("value", byteArrayOf(1)) }
      .isInstanceOf<IllegalStateException>()
    assertContents(real, "real")
  }

  private suspend fun write(storage: Storage, value: String) {
    storage.preferences("settings").edit { it[key] = value }
    storage.files.write("value", value.encodeToByteArray())
    storage.cache.write("value", value.encodeToByteArray())
  }

  private suspend fun assertContents(storage: Storage, value: String) {
    assertThat(storage.preferences("settings").data.first()[key]).isEqualTo(value)
    assertThat(storage.files.read("value")).isNotNull().isEqualTo(value.encodeToByteArray())
    assertThat(storage.cache.read("value")).isNotNull().isEqualTo(value.encodeToByteArray())
  }

  private suspend fun assertEmpty(storage: Storage) {
    assertThat(storage.preferences("settings").data.first().asMap()).isEqualTo(emptyMap())
    assertThat(storage.files.read("value")).isNull()
    assertThat(storage.cache.read("value")).isNull()
  }

  private class ModeController(mode: RuntimeMode? = null) : RuntimeModeController {
    val loadedMode = CompletableDeferred<RuntimeMode>()

    init {
      if (mode != null) {
        loadedMode.complete(mode)
      }
    }

    override suspend fun mode(): RuntimeMode = loadedMode.await()

    override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
  }
}
