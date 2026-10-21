package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.scope.Scope
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
      DefaultStorage("runtime-mode", scope.coroutineScope(), platform).also { scope.register(it) }
    assertThat(reopened.preferences("settings").data.first()[key]).isEqualTo("Fake")
    assertThat(reopened.files.read("state")).isNotNull().isEqualTo(byteArrayOf(1))
    assertThat(reopened.cache.read("state")).isNotNull().isEqualTo(byteArrayOf(2))
  }

  @Test
  fun `fake storage isolates all data from real storage`() = runTestWithScope { scope ->
    val realPlatform = InMemoryStoragePlatform()
    val real: Storage = scope.storage(realPlatform)
    val key = stringPreferencesKey("theme")
    real.preferences("settings").edit { it[key] = "real" }
    real.files.directory("books").write("chapter", byteArrayOf(1))
    real.cache.write("cover", byteArrayOf(2))
    val mode = RuntimeMode.Fake
    val factory: ScopedStorage.Factory =
      DefaultStorage.Factory(realPlatform, lazy { FakeRuntimeModeController(mode) })
    val storage: Storage = scope.storage(factory)
    val operations =
      listOf(
        async {
          val preferences = storage.preferences("settings")
          assertThat(preferences.data.first()[key]).isNull()
          preferences.edit { it[key] = "fake" }
        },
        async {
          val books = storage.files.directory("books")
          assertThat(books.read("chapter")).isNull()
          books.write("chapter", byteArrayOf(3))
        },
        async {
          assertThat(storage.cache.read("cover")).isNull()
          storage.cache.write("cover", byteArrayOf(4))
        },
        async { assertThat(storage.databasePath("podcasts")).isNull() },
      )
    operations.awaitAll()

    assertThat(storage.preferences("settings").data.first()[key]).isEqualTo("fake")
    assertThat(storage.files.directory("books").read("chapter"))
      .isNotNull()
      .isEqualTo(byteArrayOf(3))
    assertThat(storage.cache.read("cover")).isNotNull().isEqualTo(byteArrayOf(4))
    assertThat(real.preferences("settings").data.first()[key]).isEqualTo("real")
    assertThat(real.files.directory("books").read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
    assertThat(real.cache.read("cover")).isNotNull().isEqualTo(byteArrayOf(2))
  }

  @Test
  fun `a mode read failure allows retry without falling back to real storage`() =
    runTestWithScope { scope ->
      val realPlatform = InMemoryStoragePlatform()
      val real: Storage = scope.storage(realPlatform)
      val key = stringPreferencesKey("theme")
      real.preferences("settings").edit { it[key] = "real" }
      real.files.write("chapter", byteArrayOf(1))
      real.cache.write("cover", byteArrayOf(2))
      val failure = IllegalStateException("Mode unavailable")
      val controller = FakeRuntimeModeController(RuntimeMode.Fake).apply { modeFailure = failure }
      val factory: ScopedStorage.Factory = DefaultStorage.Factory(realPlatform, lazy { controller })
      val storage: Storage = scope.storage(factory)

      assertFailure { storage.preferences("settings").edit { it[key] = "fake" } }
        .isInstanceOf<IllegalStateException>()
        .hasMessage("Mode unavailable")
      assertFailure { storage.files.read("chapter") }
        .isInstanceOf<IllegalStateException>()
        .hasMessage("Mode unavailable")
      assertFailure { storage.files.write("chapter", byteArrayOf(3)) }
        .isInstanceOf<IllegalStateException>()
        .hasMessage("Mode unavailable")
      assertFailure { storage.files.delete("chapter") }
        .isInstanceOf<IllegalStateException>()
        .hasMessage("Mode unavailable")
      assertFailure { storage.cache.deleteAll() }
        .isInstanceOf<IllegalStateException>()
        .hasMessage("Mode unavailable")

      controller.modeFailure = null
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

  private fun Scope.storage(platform: StoragePlatform): Storage =
    DefaultStorage("app", coroutineScope(), platform).also { register(it) }

  private fun Scope.storage(factory: ScopedStorage.Factory): Storage =
    factory.create("app", coroutineScope()).also { register(it) }

  private class FakeRuntimeModeController(private val selectedMode: RuntimeMode) :
    RuntimeModeController {
    var modeFailure: Exception? = null

    override val mode: RuntimeMode
      get() {
        modeFailure?.let { throw it }
        return selectedMode
      }

    override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
  }
}
