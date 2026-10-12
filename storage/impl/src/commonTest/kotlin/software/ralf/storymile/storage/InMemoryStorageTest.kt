package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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

      assertThat(reopened.preferences("settings").data.first()[key]).isEqualTo("dark")
      assertThat(reopened.files.directory("books").read("chapter"))
        .isNotNull()
        .isEqualTo(byteArrayOf(1))
      assertThat(reopened.cache.read("cover")).isNotNull().isEqualTo(byteArrayOf(2))
      assertThat(fresh.preferences("settings").data.first()[key]).isNull()
      assertThat(fresh.files.directory("books").read("chapter")).isNull()
      assertThat(fresh.cache.read("cover")).isNull()
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

      assertThat(books.read("track")).isNull()
      assertThat(chapters.read("track")).isNull()
      assertThat(app.files.read("track")).isNotNull().isEqualTo(byteArrayOf(0))
      assertThat(app.files.directory("Books").read("track")).isNotNull().isEqualTo(byteArrayOf(3))
      assertThat(app.files.directory("bookshelf").read("track"))
        .isNotNull()
        .isEqualTo(byteArrayOf(4))
      assertThat(app.cache.directory("books").read("track")).isNotNull().isEqualTo(byteArrayOf(5))
      assertThat(user.files.directory("books").read("track")).isNotNull().isEqualTo(byteArrayOf(6))
      assertThat(app.preferences("settings").data.first()[key]).isEqualTo("dark")
      assertThat(app.preferences("Settings").data.first()[key]).isEqualTo("system")
      assertThat(user.preferences("settings").data.first()[key]).isEqualTo("light")

      chapters.write("track", byteArrayOf(7))
      assertThat(chapters.read("track")).isNotNull().isEqualTo(byteArrayOf(7))
      app.files.deleteAll()
      assertThat(chapters.read("track")).isNull()
      assertThat(app.files.read("track")).isNull()
      assertThat(app.cache.directory("books").read("track")).isNotNull().isEqualTo(byteArrayOf(5))
    }

  @Test
  fun `file reads and writes do not expose mutable stored bytes`() = runTestWithScope { scope ->
    val storage: Storage = scope.storage(InMemoryStorageFactory())
    val content = byteArrayOf(1, 2, 3)
    storage.files.write("track", content)
    content[0] = 9
    assertThat(storage.files.read("track")).isNotNull().given { read -> read[1] = 9 }

    assertThat(storage.files.read("track")).isNotNull().isEqualTo(byteArrayOf(1, 2, 3))
    storage.files.write("track", byteArrayOf(4))
    assertThat(storage.files.read("track")).isNotNull().isEqualTo(byteArrayOf(4))
    storage.files.delete("track")
    storage.files.delete("missing")
    assertThat(storage.files.read("track")).isNull()
  }

  @Test
  fun `preferences serialize concurrent updates and roll back failed edits`() =
    runTestWithScope { scope ->
      val storage: Storage = scope.storage(InMemoryStorageFactory())
      val preferences = storage.preferences("settings")
      val key = intPreferencesKey("count")
      var transforms = 0

      List(20) {
          async {
            preferences.edit {
              transforms++
              val previous = it[key] ?: 0
              yield()
              it[key] = previous + 1
            }
          }
        }
        .awaitAll()
      assertThat(preferences.data.first()[key]).isEqualTo(20)
      assertThat(transforms).isEqualTo(20)

      assertFailure {
        preferences.edit {
          it[key] = 100
          error("Edit failed")
        }
      }
        .isInstanceOf<IllegalStateException>()
      assertThat(preferences.data.first()[key]).isEqualTo(20)
      preferences.edit { it[key] = 21 }
      assertThat(preferences.data.first()[key]).isEqualTo(21)
    }

  @Test
  fun `preferences retain immutable snapshots of caller-owned values`() =
    runTestWithScope { scope ->
      val storage: Storage = scope.storage(InMemoryStorageFactory())
      val preferences = storage.preferences("settings")
      val key = stringPreferencesKey("theme")
      val submitted = mutablePreferencesOf(key to "dark")

      val returned = preferences.updateData { submitted }
      submitted[key] = "light"

      assertThat(returned[key]).isEqualTo("dark")
      assertThat(preferences.data.first()[key]).isEqualTo("dark")
    }

  @Test
  fun `nested updates reject the same store and allow another store`() = runTestWithScope { scope ->
    val storage: Storage = scope.storage(InMemoryStorageFactory())
    val preferences = storage.preferences("settings")
    val other = storage.preferences("other")
    val key = stringPreferencesKey("theme")

    assertFailure {
      withTimeout(1000) {
        preferences.edit {
          other.edit { preferences.updateData { it } }
        }
      }
    }
      .isInstanceOf<IllegalStateException>()
      .isNotInstanceOf<CancellationException>()

    preferences.edit { settings ->
      other.edit { it[key] = "light" }
      settings[key] = "dark"
    }
    assertThat(preferences.data.first()[key]).isEqualTo("dark")
    assertThat(other.data.first()[key]).isEqualTo("light")
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
        assertFailure {
          preferences.edit {
            it[key] = "light"
            editStarted.complete(Unit)
            try {
              awaitCancellation()
            } catch (_: CancellationException) {
              // An edit that swallows cancellation must not commit.
            }
          }
        }
          .isInstanceOf<CancellationException>()
      }
      editStarted.await()

      original.onExitScope()
      editing.await()
      val reopened: Storage = scope.storage(factory)

      assertThat(reopened.preferences("settings").data.first()[key]).isEqualTo("dark")
    }

  private fun Scope.storage(factory: ScopedStorage.Factory, namespace: String = "app"): Storage =
    factory.create(namespace, coroutineScope()).also { register(it) }
}
