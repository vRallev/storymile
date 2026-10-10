package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.runTestWithScope

class InMemoryStorageTest {
  @Test
  fun `data survives scope exit within a factory and new factories start empty`() =
    runTestWithScope { scope ->
      val factory: ScopedStorage.Factory = InMemoryStorageFactory()
      val original: ScopedStorage = factory.create("app", scope.coroutineScope())
      scope.register(original)
      val key = stringPreferencesKey("theme")
      original.preferences("settings").edit { it[key] = "dark" }
      original.files.directory("books").write("chapter", byteArrayOf(1))
      original.cache.write("cover", byteArrayOf(2))

      original.onExitScope()
      val reopened: Storage = scope.storage(factory)
      val fresh: Storage = scope.storage(InMemoryStorageFactory())

      assertEquals("dark", reopened.preferences("settings").data.first()[key])
      assertContentEquals(byteArrayOf(1), reopened.files.directory("books").read("chapter"))
      assertContentEquals(byteArrayOf(2), reopened.cache.read("cover"))
      assertNull(fresh.preferences("settings").data.first()[key])
      assertNull(fresh.files.directory("books").read("chapter"))
      assertNull(fresh.cache.read("cover"))
    }

  @Test
  fun `clearing nested files preserves siblings caches preferences and other namespaces`() =
    runTestWithScope { scope ->
      val factory: ScopedStorage.Factory = InMemoryStorageFactory()
      val app: Storage = scope.storage(factory)
      val user: Storage = scope.storage(factory, "user-alice")
      val key = stringPreferencesKey("theme")
      app.preferences("settings").edit { it[key] = "dark" }
      user.preferences("settings").edit { it[key] = "light" }
      app.preferences("Settings").edit { it[key] = "system" }
      app.files.write("track", byteArrayOf(0))
      val books: FileStorage = app.files.directory("books")
      val chapters: FileStorage = books.directory("chapters")
      books.write("track", byteArrayOf(1))
      chapters.write("track", byteArrayOf(2))
      app.files.directory("Books").write("track", byteArrayOf(3))
      app.files.directory("bookshelf").write("track", byteArrayOf(4))
      app.cache.directory("books").write("track", byteArrayOf(5))
      user.files.directory("books").write("track", byteArrayOf(6))

      books.deleteAll()

      assertNull(books.read("track"))
      assertNull(chapters.read("track"))
      assertContentEquals(byteArrayOf(0), app.files.read("track"))
      assertContentEquals(byteArrayOf(3), app.files.directory("Books").read("track"))
      assertContentEquals(byteArrayOf(4), app.files.directory("bookshelf").read("track"))
      assertContentEquals(byteArrayOf(5), app.cache.directory("books").read("track"))
      assertContentEquals(byteArrayOf(6), user.files.directory("books").read("track"))
      assertEquals("dark", app.preferences("settings").data.first()[key])
      assertEquals("system", app.preferences("Settings").data.first()[key])
      assertEquals("light", user.preferences("settings").data.first()[key])

      chapters.write("track", byteArrayOf(7))
      assertContentEquals(byteArrayOf(7), chapters.read("track"))
      app.files.deleteAll()
      assertNull(chapters.read("track"))
      assertNull(app.files.read("track"))
      assertContentEquals(byteArrayOf(5), app.cache.directory("books").read("track"))
    }

  @Test
  fun `file reads and writes do not expose mutable stored bytes`() = runTestWithScope { scope ->
    val storage: Storage = scope.storage(InMemoryStorageFactory())
    val content = byteArrayOf(1, 2, 3)
    storage.files.write("track", content)
    content[0] = 9
    val read = assertNotNull(storage.files.read("track"))
    read[1] = 9

    assertContentEquals(byteArrayOf(1, 2, 3), storage.files.read("track"))
    storage.files.write("track", byteArrayOf(4))
    assertContentEquals(byteArrayOf(4), storage.files.read("track"))
    storage.files.delete("track")
    storage.files.delete("missing")
    assertNull(storage.files.read("track"))
  }

  @Test
  fun `preferences serialize concurrent updates and roll back failed edits`() =
    runTestWithScope { scope ->
      val storage: Storage = scope.storage(InMemoryStorageFactory())
      val preferences = storage.preferences("settings")
      val key = intPreferencesKey("count")

      List(20) {
          async {
            preferences.edit {
              val previous = it[key] ?: 0
              yield()
              it[key] = previous + 1
            }
          }
        }
        .awaitAll()
      assertEquals(20, preferences.data.first()[key])

      assertFailsWith<IllegalStateException> {
        preferences.edit {
          it[key] = 100
          error("Edit failed")
        }
      }
      assertEquals(20, preferences.data.first()[key])
      preferences.edit { it[key] = 21 }
      assertEquals(21, preferences.data.first()[key])
    }

  @Test
  fun `scope exit cancels a preference edit and reopening keeps the last committed value`() =
    runTestWithScope { scope ->
      val factory: ScopedStorage.Factory = InMemoryStorageFactory()
      val original: ScopedStorage = factory.create("app", scope.coroutineScope())
      scope.register(original)
      val preferences = original.preferences("settings")
      val key = stringPreferencesKey("theme")
      preferences.edit { it[key] = "dark" }
      val editStarted = CompletableDeferred<Unit>()
      val editing = async {
        assertFailsWith<CancellationException> {
          preferences.edit {
            it[key] = "light"
            editStarted.complete(Unit)
            awaitCancellation()
          }
        }
      }
      editStarted.await()

      original.onExitScope()
      editing.await()
      val reopened: Storage = scope.storage(factory)

      assertEquals("dark", reopened.preferences("settings").data.first()[key])
    }

  private fun Scope.storage(factory: ScopedStorage.Factory, namespace: String = "app"): Storage =
    factory.create(namespace, coroutineScope()).also { register(it) }
}
