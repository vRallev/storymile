@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile.storage

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

class RuntimeModeStorageTest {
  @Test
  fun `retained handles isolate preferences files caches and directories across modes`() =
    runTestWithScope { scope ->
      val controller: RuntimeModeController = ModeController()
      val factory: ScopedStorage.Factory =
        RuntimeModeStorageFactory(InMemoryStorageFactory(), controller)
      val storage: Storage = scope.storage(factory)
      val preferences = storage.preferences("settings")
      val key = stringPreferencesKey("theme")
      val files: FileStorage = storage.files.directory("books").directory("chapters")
      val cache: FileStorage = storage.cache.directory("books")
      preferences.edit { it[key] = "real" }
      files.write("chapter", byteArrayOf(1))
      cache.write("cover", byteArrayOf(2))

      controller.switchMode(RuntimeMode.Fake)

      assertSame(preferences, storage.preferences("settings"))
      assertNull(preferences.data.first()[key])
      assertNull(files.read("chapter"))
      assertNull(cache.read("cover"))
      preferences.edit { it[key] = "fake" }
      files.write("chapter", byteArrayOf(3))
      cache.write("cover", byteArrayOf(4))

      controller.switchMode(RuntimeMode.Real)

      assertEquals("real", preferences.data.first()[key])
      assertContentEquals(byteArrayOf(1), files.read("chapter"))
      assertContentEquals(byteArrayOf(2), cache.read("cover"))

      controller.switchMode(RuntimeMode.Fake)

      assertEquals("fake", preferences.data.first()[key])
      assertContentEquals(byteArrayOf(3), files.read("chapter"))
      assertContentEquals(byteArrayOf(4), cache.read("cover"))
      files.delete("chapter")
      cache.deleteAll()
      preferences.edit { it.remove(key) }
      assertNull(files.read("chapter"))
      assertNull(cache.read("cover"))

      controller.switchMode(RuntimeMode.Real)

      assertEquals("real", preferences.data.first()[key])
      assertContentEquals(byteArrayOf(1), files.read("chapter"))
      assertContentEquals(byteArrayOf(2), cache.read("cover"))
    }

  @Test
  fun `active preference collectors follow mode changes without reacquiring the store`() =
    runTestWithScope { scope ->
      val controller: RuntimeModeController = ModeController()
      val factory: ScopedStorage.Factory =
        RuntimeModeStorageFactory(InMemoryStorageFactory(), controller)
      val storage: Storage = scope.storage(factory)
      val preferences = storage.preferences("settings")
      val key = stringPreferencesKey("theme")
      preferences.edit { it[key] = "real" }
      val values = Channel<Preferences>()
      val collecting = backgroundScope.launch { preferences.data.collect { values.send(it) } }

      assertEquals("real", values.receive()[key])
      controller.switchMode(RuntimeMode.Fake)
      assertNull(values.receive()[key])
      preferences.edit { it[key] = "fake" }
      assertEquals("fake", values.receive()[key])
      controller.switchMode(RuntimeMode.Real)
      assertEquals("real", values.receive()[key])

      collecting.cancelAndJoin()
    }

  @Test
  fun `operations wait for the restored mode before accessing a backend`() =
    runTestWithScope { scope ->
      val controller: RuntimeModeController = ModeController(initialized = false)
      val factory: ScopedStorage.Factory =
        RuntimeModeStorageFactory(InMemoryStorageFactory(), controller)
      val storage: Storage = scope.storage(factory)
      val key = stringPreferencesKey("theme")
      val writing = async { storage.files.write("chapter", byteArrayOf(1)) }
      val editing = async { storage.preferences("settings").edit { it[key] = "fake" } }
      runCurrent()
      assertFalse(writing.isCompleted)
      assertFalse(editing.isCompleted)

      controller.switchMode(RuntimeMode.Fake)
      writing.await()
      editing.await()

      assertContentEquals(byteArrayOf(1), storage.files.read("chapter"))
      assertEquals("fake", storage.preferences("settings").data.first()[key])
      controller.switchMode(RuntimeMode.Real)
      assertNull(storage.files.read("chapter"))
      assertNull(storage.preferences("settings").data.first()[key])
    }

  @Test
  fun `mode switches cancel suspended edits without retrying or committing them`() =
    runTestWithScope { scope ->
      val controller: RuntimeModeController = ModeController()
      val factory: ScopedStorage.Factory =
        RuntimeModeStorageFactory(InMemoryStorageFactory(), controller)
      val storage: Storage = scope.storage(factory)
      val preferences = storage.preferences("settings")
      val key = stringPreferencesKey("theme")
      var transforms = 0
      preferences.edit { it[key] = "real" }
      val editStarted = CompletableDeferred<Unit>()
      val editing = async {
        assertFailsWith<CancellationException> {
          preferences.edit {
            transforms++
            it[key] = "uncommitted"
            editStarted.complete(Unit)
            runCatching { awaitCancellation() }
          }
        }
      }
      editStarted.await()

      controller.switchMode(RuntimeMode.Fake)
      editing.await()

      assertEquals(1, transforms)
      assertNull(preferences.data.first()[key])
      controller.switchMode(RuntimeMode.Real)
      assertEquals("real", preferences.data.first()[key])
      preferences.edit { it[key] = "still usable" }
      assertEquals("still usable", preferences.data.first()[key])

      assertFailsWith<CancellationException> {
        preferences.edit {
          it[key] = "uncommitted"
          controller.switchMode(RuntimeMode.Fake)
        }
      }
      controller.switchMode(RuntimeMode.Real)
      assertEquals("still usable", preferences.data.first()[key])
    }

  @Test
  fun `scope exit cancels retained handles and fake data belongs to the graph factory`() =
    runTestWithScope { scope ->
      val controller: RuntimeModeController = ModeController(RuntimeMode.Fake)
      val persistentFactory: ScopedStorage.Factory = InMemoryStorageFactory()
      val factory: ScopedStorage.Factory = RuntimeModeStorageFactory(persistentFactory, controller)
      val original: ScopedStorage = factory.create("app", scope.coroutineScope())
      scope.register(original)
      val preferences = original.preferences("settings")
      val key = stringPreferencesKey("theme")
      preferences.edit { it[key] = "fake" }
      original.files.write("chapter", byteArrayOf(1))
      original.onExitScope()

      assertFailsWith<CancellationException> { original.files.read("chapter") }
      assertFailsWith<CancellationException> { original.cache.directory("books") }
      assertFailsWith<CancellationException> { preferences.data.first() }
      assertFailsWith<CancellationException> { preferences.edit { it[key] = "closed" } }
      val reopened: Storage = scope.storage(factory)
      val fresh: Storage = scope.storage(RuntimeModeStorageFactory(persistentFactory, controller))

      assertEquals("fake", reopened.preferences("settings").data.first()[key])
      assertContentEquals(byteArrayOf(1), reopened.files.read("chapter"))
      assertNull(fresh.preferences("settings").data.first()[key])
      assertNull(fresh.files.read("chapter"))
    }

  private fun Scope.storage(factory: ScopedStorage.Factory): Storage =
    factory.create("app", coroutineScope()).also { register(it) }

  private class ModeController(
    initialMode: RuntimeMode = RuntimeMode.Real,
    initialized: Boolean = true,
  ) : RuntimeModeController {
    private val initialization = CompletableDeferred<Unit>()

    override val mode: StateFlow<RuntimeMode>
      field = MutableStateFlow(initialMode)

    init {
      if (initialized) {
        initialization.complete(Unit)
      }
    }

    override suspend fun awaitInitialization() {
      initialization.await()
    }

    override fun setInitialMode(mode: RuntimeMode) {
      switchMode(mode)
    }

    override fun switchMode(mode: RuntimeMode) {
      this.mode.value = mode
      initialization.complete(Unit)
    }
  }
}
