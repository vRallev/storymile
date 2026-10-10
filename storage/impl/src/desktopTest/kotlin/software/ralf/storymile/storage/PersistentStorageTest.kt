@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.runTestWithScope

class PersistentStorageTest {
  @Test
  fun `app and user namespaces keep identical preference and file names separate`() =
    storageTest { scope, platform, _ ->
      val key = stringPreferencesKey("selection")
      val users =
        listOf("app", "user-alice", "user-Alice").map { namespace ->
          val owner = if (namespace == "app") scope else scope.child(this)
          val storage: Storage = owner.storage(namespace, platform)
          storage.preferences("settings").edit { it[key] = namespace }
          storage.files.write("settings", namespace.encodeToByteArray())
          namespace to storage
        }

      users.forEach { (namespace, storage) ->
        assertThat(storage.preferences("settings").data.first()[key]).isEqualTo(namespace)
        assertContentEquals(namespace.encodeToByteArray(), storage.files.read("settings"))
      }
      users[1].second.files.delete("settings")
      assertNull(users[1].second.files.read("settings"))
      assertContentEquals("user-Alice".encodeToByteArray(), users[2].second.files.read("settings"))
      assertThat(users[1].second.preferences("settings").data.first()[key]).isEqualTo("user-alice")
    }

  @Test
  fun `nested preference updates fail promptly and leave the store usable`() =
    storageTest { scope, platform, _ ->
      val storage: Storage = scope.storage("app", platform)
      val preferences = storage.preferences("settings")
      val key = stringPreferencesKey("theme")

      val failure =
        assertFailsWith<IllegalStateException> {
          withTimeout(1000) {
            preferences.updateData { current ->
              preferences.updateData { it }
              current
            }
          }
        }
      assertFalse(failure is CancellationException)

      preferences.edit { it[key] = "dark" }
      assertThat(preferences.data.first()[key]).isEqualTo("dark")
    }

  @Test
  fun `case-sensitive preference and file names remain distinct in one namespace`() =
    storageTest { scope, platform, _ ->
      val key = stringPreferencesKey("selection")
      val originalScope = scope.child(this)
      val original: Storage = originalScope.storage("app", platform)
      listOf("settings", "Settings").forEach { name ->
        original.preferences(name).edit { it[key] = name }
        original.files.write(name, name.encodeToByteArray())
      }

      originalScope.destroy()
      val reopened: Storage = scope.child(this).storage("app", platform)

      listOf("settings", "Settings").forEach { name ->
        assertThat(reopened.preferences(name).data.first()[key]).isEqualTo(name)
        assertContentEquals(name.encodeToByteArray(), reopened.files.read(name))
      }
      reopened.files.delete("settings")
      assertContentEquals("Settings".encodeToByteArray(), reopened.files.read("Settings"))
    }

  @Test
  fun `reopening a closed namespace restores its preferences and files`() =
    storageTest { scope, platform, _ ->
      val key = stringPreferencesKey("theme")
      val originalScope = scope.child(this)
      val original: Storage = originalScope.storage("app", platform)
      original.preferences("settings").edit { it[key] = "dark" }
      original.files.write("recording", byteArrayOf(1, 2, 3))

      originalScope.destroy()
      val replacementScope = scope.child(this)
      try {
        val reopened: Storage = replacementScope.storage("app", platform)
        assertThat(reopened.preferences("settings").data.first()[key]).isEqualTo("dark")
        assertContentEquals(byteArrayOf(1, 2, 3), reopened.files.read("recording"))
      } finally {
        replacementScope.destroy()
      }
    }

  @Test
  fun `file replacement and deletion do not affect other stored files`() =
    storageTest { scope, platform, _ ->
      val storage: Storage = scope.storage("app", platform)
      storage.files.write("selected", byteArrayOf(1, 2, 3))
      storage.files.write("other", byteArrayOf(4))

      storage.files.write("selected", byteArrayOf(9))

      assertContentEquals(byteArrayOf(9), storage.files.read("selected"))
      storage.files.delete("selected")
      storage.files.delete("missing")
      assertNull(storage.files.read("selected"))
      assertContentEquals(byteArrayOf(4), storage.files.read("other"))
    }

  @Test
  fun `nested folders preserve files and isolate sibling folders and scopes`() =
    storageTest { scope, platform, _ ->
      val appScope = scope.child(this)
      val app: Storage = appScope.storage("app", platform)
      val user: Storage = scope.child(this).storage("user-alice", platform)
      app.files.write("track", byteArrayOf(0))
      app.files.directory("Books").directory("audio").write("track", byteArrayOf(1))
      app.files.directory("books").directory("audio").write("track", byteArrayOf(2))
      user.files.directory("Books").directory("audio").write("track", byteArrayOf(3))

      appScope.destroy()
      val reopened: Storage = scope.child(this).storage("app", platform)
      val books: FileStorage = reopened.files.directory("Books").directory("audio")
      val otherBooks: FileStorage = reopened.files.directory("books").directory("audio")

      assertContentEquals(byteArrayOf(0), reopened.files.read("track"))
      assertContentEquals(byteArrayOf(1), books.read("track"))
      assertContentEquals(byteArrayOf(2), otherBooks.read("track"))
      otherBooks.delete("track")
      assertNull(otherBooks.read("track"))
      assertContentEquals(byteArrayOf(1), books.read("track"))
      assertContentEquals(
        byteArrayOf(3),
        user.files.directory("Books").directory("audio").read("track"),
      )
    }

  @Test
  fun `reading or deleting a missing nested file does not create folders`() =
    storageTest { scope, platform, root ->
      val storage: Storage = scope.storage("app", platform)
      val folder: FileStorage = storage.files.directory("books").directory("audio")

      assertNull(folder.read("missing"))
      folder.delete("missing")

      assertFalse(FileSystem.SYSTEM.exists(root / "app".encodeUtf8().hex()))
    }

  @Test
  fun `cache folders keep files separate from durable data and other scopes`() =
    storageTest { scope, platform, _ ->
      val app: Storage = scope.storage("app", platform)
      val user: Storage = scope.child(this).storage("user-alice", platform)
      val key = stringPreferencesKey("theme")
      app.preferences("settings").edit { it[key] = "dark" }
      app.files.directory("audio").write("track", byteArrayOf(1))
      app.cache.directory("audio").write("track", byteArrayOf(2))
      user.cache.directory("audio").write("track", byteArrayOf(3))

      assertContentEquals(byteArrayOf(1), app.files.directory("audio").read("track"))
      assertContentEquals(byteArrayOf(2), app.cache.directory("audio").read("track"))
      assertContentEquals(byteArrayOf(3), user.cache.directory("audio").read("track"))
      app.cache.directory("audio").delete("track")
      assertNull(app.cache.directory("audio").read("track"))
      assertContentEquals(byteArrayOf(1), app.files.directory("audio").read("track"))
      assertContentEquals(byteArrayOf(3), user.cache.directory("audio").read("track"))
      assertThat(app.preferences("settings").data.first()[key]).isEqualTo("dark")
    }

  @Test
  fun `removed cache files can be recreated without affecting durable data`() =
    storageTest { scope, platform, root ->
      val storage: Storage = scope.storage("app", platform)
      val cache: FileStorage = storage.cache.directory("audio")
      val key = stringPreferencesKey("theme")
      storage.preferences("settings").edit { it[key] = "dark" }
      storage.files.write("track", byteArrayOf(1))
      cache.write("track", byteArrayOf(2))

      FileSystem.SYSTEM.deleteRecursively(root / "cache")

      assertNull(cache.read("track"))
      cache.delete("missing")
      assertFalse(FileSystem.SYSTEM.exists(root / "cache"))
      cache.write("track", byteArrayOf(3))
      assertContentEquals(byteArrayOf(3), cache.read("track"))
      assertContentEquals(byteArrayOf(1), storage.files.read("track"))
      assertThat(storage.preferences("settings").data.first()[key]).isEqualTo("dark")
    }

  @Test
  fun `clearing a folder removes its descendants and preserves other stored data`() =
    storageTest { scope, platform, _ ->
      val app: Storage = scope.storage("app", platform)
      val user: Storage = scope.child(this).storage("user-alice", platform)
      val books: FileStorage = app.files.directory("books")
      val chapters: FileStorage = books.directory("chapters")
      val key = stringPreferencesKey("theme")
      app.preferences("settings").edit { it[key] = "dark" }
      app.files.write("track", byteArrayOf(0))
      books.write("track", byteArrayOf(1))
      chapters.write("track", byteArrayOf(2))
      app.files.directory("other").write("track", byteArrayOf(3))
      app.cache.directory("books").write("track", byteArrayOf(4))
      user.files.directory("books").write("track", byteArrayOf(5))

      books.deleteAll()

      assertNull(books.read("track"))
      assertNull(chapters.read("track"))
      assertContentEquals(byteArrayOf(0), app.files.read("track"))
      assertContentEquals(byteArrayOf(3), app.files.directory("other").read("track"))
      assertContentEquals(byteArrayOf(4), app.cache.directory("books").read("track"))
      assertContentEquals(byteArrayOf(5), user.files.directory("books").read("track"))
      assertThat(app.preferences("settings").data.first()[key]).isEqualTo("dark")
      chapters.write("track", byteArrayOf(6))
      assertContentEquals(byteArrayOf(6), chapters.read("track"))
    }

  @Test
  fun `clearing a storage root only removes files in that area and namespace`() =
    storageTest { scope, platform, _ ->
      val app: Storage = scope.storage("app", platform)
      val user: Storage = scope.child(this).storage("user-alice", platform)
      val key = stringPreferencesKey("theme")
      app.preferences("settings").edit { it[key] = "dark" }
      app.files.write("track", byteArrayOf(1))
      app.files.directory("audio").write("track", byteArrayOf(2))
      app.cache.write("track", byteArrayOf(3))
      app.cache.directory("audio").write("track", byteArrayOf(4))
      user.files.write("track", byteArrayOf(5))

      app.files.deleteAll()

      assertNull(app.files.read("track"))
      assertNull(app.files.directory("audio").read("track"))
      assertContentEquals(byteArrayOf(3), app.cache.read("track"))
      assertContentEquals(byteArrayOf(4), app.cache.directory("audio").read("track"))
      app.files.write("track", byteArrayOf(6))
      app.cache.deleteAll()
      assertNull(app.cache.read("track"))
      assertNull(app.cache.directory("audio").read("track"))
      assertContentEquals(byteArrayOf(6), app.files.read("track"))
      assertContentEquals(byteArrayOf(5), user.files.read("track"))
      assertThat(app.preferences("settings").data.first()[key]).isEqualTo("dark")
    }

  @Test
  fun `clearing missing folders does not create directories`() =
    storageTest { scope, platform, root ->
      val storage: Storage = scope.storage("app", platform)
      val missing: FileStorage = storage.files.directory("missing").directory("nested")

      missing.deleteAll()
      storage.files.deleteAll()
      storage.cache.deleteAll()

      assertFalse(FileSystem.SYSTEM.exists(root / "app".encodeUtf8().hex()))
      assertFalse(FileSystem.SYSTEM.exists(root / "cache"))
      missing.write("track", byteArrayOf(1))
      missing.deleteAll()
      missing.deleteAll()
      missing.write("track", byteArrayOf(2))
      assertContentEquals(byteArrayOf(2), missing.read("track"))
    }

  @Test
  fun `recursive clear removes symlinks without deleting their targets`() =
    storageTest { scope, platform, root ->
      val app: Storage = scope.storage("app", platform)
      val user: Storage = scope.child(this).storage("user-alice", platform)
      val books: FileStorage = app.files.directory("books")
      val privateFiles: FileStorage = user.files.directory("private").directory("nested")
      books.write("own", byteArrayOf(1))
      privateFiles.write("track", byteArrayOf(2))
      val booksPath = root / "app".encodeUtf8().hex() / "files" / "books".encodeUtf8().hex()
      val privatePath =
        root / "user-alice".encodeUtf8().hex() / "files" / "private".encodeUtf8().hex()
      val directoryLink = booksPath / "directory-link".encodeUtf8().hex()
      val fileLink = booksPath / "file-link".encodeUtf8().hex()
      Files.createSymbolicLink(directoryLink.toNioPath(), privatePath.toNioPath())
      Files.createSymbolicLink(
        fileLink.toNioPath(),
        (privatePath / "nested".encodeUtf8().hex() / "track".encodeUtf8().hex()).toNioPath(),
      )

      books.deleteAll()

      assertNull(books.read("own"))
      assertFalse(FileSystem.SYSTEM.exists(directoryLink))
      assertFalse(FileSystem.SYSTEM.exists(fileLink))
      assertContentEquals(byteArrayOf(2), privateFiles.read("track"))
    }

  @Test
  fun `symlinks cannot route file access into another namespace`() =
    storageTest { scope, platform, root ->
      val app: Storage = scope.storage("app", platform)
      val user: Storage = scope.child(this).storage("user-alice", platform)
      app.files.write("own", byteArrayOf(1))
      user.files.write("private", byteArrayOf(2))
      Files.createSymbolicLink(
        (root / "app".encodeUtf8().hex() / "files" / "linked".encodeUtf8().hex()).toNioPath(),
        (root / "user-alice".encodeUtf8().hex() / "files" / "private".encodeUtf8().hex())
          .toNioPath(),
      )

      assertFailsWith<IllegalStateException> { app.files.read("linked") }
      assertFailsWith<IllegalStateException> { app.files.write("linked", byteArrayOf(9)) }
      assertFailsWith<IllegalStateException> { app.files.delete("linked") }
      assertContentEquals(byteArrayOf(2), user.files.read("private"))
    }

  @Test
  fun `symlink folders cannot route nested file access into another namespace`() =
    storageTest { scope, platform, root ->
      val app: Storage = scope.storage("app", platform)
      val user: Storage = scope.child(this).storage("user-alice", platform)
      val privateFiles: FileStorage = user.files.directory("private").directory("nested")
      app.files.write("own", byteArrayOf(1))
      privateFiles.write("track", byteArrayOf(2))
      Files.createSymbolicLink(
        (root / "app".encodeUtf8().hex() / "files" / "linked".encodeUtf8().hex()).toNioPath(),
        (root / "user-alice".encodeUtf8().hex() / "files" / "private".encodeUtf8().hex())
          .toNioPath(),
      )
      val linked: FileStorage = app.files.directory("linked").directory("nested")

      assertFailsWith<IllegalStateException> { linked.read("track") }
      assertFailsWith<IllegalStateException> { linked.write("track", byteArrayOf(9)) }
      assertFailsWith<IllegalStateException> { linked.delete("track") }
      assertFailsWith<IllegalStateException> { linked.deleteAll() }
      assertFailsWith<IllegalStateException> { app.files.directory("linked").deleteAll() }
      assertContentEquals(byteArrayOf(2), privateFiles.read("track"))
    }

  private fun Scope.child(testScope: TestScope): Scope {
    return buildChild("user") {
      addCoroutineScopeScoped(
        CoroutineScopeScoped(
          testScope.backgroundScope.coroutineContext +
            SupervisorJob(testScope.backgroundScope.coroutineContext.job) +
            CoroutineName("user"),
        ),
      )
    }
  }

  private fun storageTest(
    block: suspend TestScope.(Scope, StoragePlatform, Path) -> Unit,
  ): TestResult = runTestWithScope { scope ->
    val root = Files.createTempDirectory("storymile-storage").toString().toPath()
    val platform =
      object : OkioStoragePlatform(FileSystem.SYSTEM, StandardTestDispatcher(testScheduler)) {
        override fun rootDirectory(): Path = root

        override fun cacheDirectory(): Path = root / "cache"
      }
    try {
      block(scope, platform, root)
    } finally {
      scope.destroy()
      runCurrent()
      FileSystem.SYSTEM.deleteRecursively(root)
    }
  }
}
