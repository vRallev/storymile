package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

class PersistentStorageTest {
  @Test
  fun `directory paths create isolated roots and nested directories`() =
    storageTest { scope, platform, _ ->
      val app: Storage = scope.storage("app", platform)
      val other: Storage = scope.child(this).storage("App", platform)
      val files = requireNotNull(app.files.path)
      val cache = requireNotNull(app.cache.path)
      val nested = requireNotNull(app.files.directory("audio").directory("clips").path)
      val paths =
        setOf(
          files,
          cache,
          nested,
          app.files.directory("Audio").directory("clips").path,
          app.cache.directory("audio").directory("clips").path,
          other.files.path,
          other.cache.path,
        )

      assertThat(paths.size).isEqualTo(7)
      paths.forEach { path ->
        assertThat(requireNotNull(path).isAbsolute).isTrue()
        assertThat(FileSystem.SYSTEM.metadata(path).isDirectory).isTrue()
      }
      assertThat(nested.parent?.parent).isEqualTo(files)
      assertThat(app.files.directory("audio").directory("clips").path).isEqualTo(nested)
    }

  @Test
  fun `directory paths initialize lazily and stay cached after clearing`() =
    storageTest { scope, platform, root ->
      val storage: Storage = scope.storage("app", platform)
      val directory: FileStorage = storage.cache.directory("coil")

      assertThat(FileSystem.SYSTEM.list(root)).isEmpty()
      val path = requireNotNull(directory.path)
      assertThat(FileSystem.SYSTEM.metadata(path).isDirectory).isTrue()
      assertThat(directory.path).isSameInstanceAs(path)

      storage.cache.deleteAll()

      assertThat(FileSystem.SYSTEM.exists(path)).isFalse()
      assertThat(directory.path).isSameInstanceAs(path)
      assertThat(FileSystem.SYSTEM.exists(path)).isFalse()
    }

  @Test
  fun `directory paths stay available after scope exit`() = storageTest { scope, platform, _ ->
    val storage: Storage = scope.storage("app", platform)
    val directory: FileStorage = storage.files.directory("library")
    val path = requireNotNull(directory.path)
    val uncached: FileStorage = storage.cache.directory("library")

    scope.destroy()

    assertThat(directory.path).isSameInstanceAs(path)
    assertThat(uncached.path).isNotNull()
  }

  @Test
  fun `fake mode returns no directory paths on first access`() =
    storageTest { scope, platform, root ->
      val factory: Storage.Factory =
        DefaultStorage.Factory(platform, lazy { FakeRuntimeModeController(RuntimeMode.Fake) })
      val storage: Storage = factory.create("app", scope.coroutineScope())

      assertThat(storage.files.path).isNull()
      assertThat(storage.cache.directory("coil").path).isNull()
      assertThat(FileSystem.SYSTEM.list(root)).isEmpty()
    }

  @Test
  fun `library files persist within their namespace and clear with their storage area`() =
    storageTest { scope, platform, _ ->
      val originalScope = scope.child(this)
      val original: Storage = originalScope.storage("app", platform)
      val cache = requireNotNull(original.cache.directory("coil").path)
      val durable = requireNotNull(original.files.directory("coil").path)
      FileSystem.SYSTEM.write(cache / "journal") { writeUtf8("cached") }
      FileSystem.SYSTEM.write(durable / "journal") { writeUtf8("durable") }
      originalScope.destroy()

      val reopened: Storage = scope.child(this).storage("app", platform)
      val sameCache = requireNotNull(reopened.cache.directory("coil").path)
      assertThat(sameCache).isEqualTo(cache)
      assertThat(FileSystem.SYSTEM.read(sameCache / "journal") { readUtf8() }).isEqualTo("cached")
      val other: Storage = scope.child(this).storage("user-guest", platform)
      val otherCache = requireNotNull(other.cache.directory("coil").path)
      FileSystem.SYSTEM.write(otherCache / "journal") { writeUtf8("other") }

      reopened.cache.deleteAll()

      assertThat(FileSystem.SYSTEM.exists(cache)).isFalse()
      assertThat(FileSystem.SYSTEM.read(durable / "journal") { readUtf8() }).isEqualTo("durable")
      assertThat(FileSystem.SYSTEM.read(otherCache / "journal") { readUtf8() }).isEqualTo("other")
      assertThat(reopened.cache.directory("coil").path).isEqualTo(cache)
      assertThat(FileSystem.SYSTEM.metadata(cache).isDirectory).isTrue()
    }

  @Test
  fun `concurrent namespace claims allow only one active owner`() = runTestWithScope { scope ->
    val factory: Storage.Factory = InMemoryStorageFactory()
    val owners = List(32) { scope.child(this).coroutineScope() }
    val storages =
      owners
        .map { owner ->
          async(Dispatchers.Default) {
            try {
              factory.create("app", owner)
            } catch (exception: IllegalStateException) {
              assertThat(exception)
                .hasMessage("Storage namespace is already owned by another active scope.")
              null
            }
          }
        }
        .awaitAll()
        .filterNotNull()

    assertThat(storages.size).isEqualTo(1)
    val storage: Storage = storages.single()
    storage.files.write("file", byteArrayOf(1))
    assertThat(storage.files.read("file")).isNotNull().isEqualTo(byteArrayOf(1))
  }

  @Test
  fun `concurrent preference lookups share one usable datastore`() =
    storageTest { scope, platform, _ ->
      val storage: Storage = scope.storage("app", platform)
      val preferences =
        List(32) { async(Dispatchers.Default) { storage.preferences("settings") } }.awaitAll()
      preferences.forEach { assertThat(it).isSameInstanceAs(preferences.first()) }

      val key = stringPreferencesKey("theme")
      preferences.first().edit { it[key] = "dark" }
      assertThat(preferences.last().data.first()[key]).isEqualTo("dark")
    }

  @Test
  fun `database paths survive reopening and isolate user and database names`() =
    storageTest { scope, platform, _ ->
      val originalScope = scope.child(this)
      val original: Storage = originalScope.storage(namespace = "user-alice", platform = platform)
      val path = requireNotNull(original.databasePath("podcasts")).toPath()
      val otherUser: Storage =
        scope.child(this).storage(namespace = "user-Alice", platform = platform)
      val paths =
        setOf(
          path.toString(),
          original.databasePath("Podcasts"),
          otherUser.databasePath("podcasts"),
        )

      assertThat(paths.size).isEqualTo(3)
      assertThat(path.isAbsolute).isTrue()
      assertThat(FileSystem.SYSTEM.metadata(requireNotNull(path.parent)).isDirectory).isTrue()
      assertThat(FileSystem.SYSTEM.exists(path)).isFalse()

      originalScope.destroy()
      val reopened: Storage =
        scope.child(this).storage(namespace = "user-alice", platform = platform)
      assertThat(reopened.databasePath("podcasts")).isEqualTo(path.toString())
    }

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
        assertThat(storage.files.read("settings"))
          .isNotNull()
          .isEqualTo(namespace.encodeToByteArray())
      }
      users[1].second.files.delete("settings")
      assertThat(users[1].second.files.read("settings")).isNull()
      assertThat(users[2].second.files.read("settings"))
        .isNotNull()
        .isEqualTo("user-Alice".encodeToByteArray())
      assertThat(users[1].second.preferences("settings").data.first()[key]).isEqualTo("user-alice")
    }

  @Test
  fun `nested preference updates fail promptly and leave the store usable`() =
    storageTest { scope, platform, _ ->
      val storage: Storage = scope.storage("app", platform)
      val preferences = storage.preferences("settings")
      val key = stringPreferencesKey("theme")

      assertFailure {
        withTimeout(1000) {
          preferences.updateData { current ->
            preferences.updateData { it }
            current
          }
        }
      }
        .isInstanceOf<IllegalStateException>()
        .isNotInstanceOf<CancellationException>()

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
        assertThat(reopened.files.read(name)).isNotNull().isEqualTo(name.encodeToByteArray())
      }
      reopened.files.delete("settings")
      assertThat(reopened.files.read("Settings"))
        .isNotNull()
        .isEqualTo("Settings".encodeToByteArray())
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
        assertThat(reopened.files.read("recording")).isNotNull().isEqualTo(byteArrayOf(1, 2, 3))
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

      assertThat(storage.files.read("selected")).isNotNull().isEqualTo(byteArrayOf(9))
      storage.files.delete("selected")
      storage.files.delete("missing")
      assertThat(storage.files.read("selected")).isNull()
      assertThat(storage.files.read("other")).isNotNull().isEqualTo(byteArrayOf(4))
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

      assertThat(reopened.files.read("track")).isNotNull().isEqualTo(byteArrayOf(0))
      assertThat(books.read("track")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(otherBooks.read("track")).isNotNull().isEqualTo(byteArrayOf(2))
      otherBooks.delete("track")
      assertThat(otherBooks.read("track")).isNull()
      assertThat(books.read("track")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(user.files.directory("Books").directory("audio").read("track"))
        .isNotNull()
        .isEqualTo(byteArrayOf(3))
    }

  @Test
  fun `reading or deleting a missing nested file does not create folders`() =
    storageTest { scope, platform, root ->
      val storage: Storage = scope.storage("app", platform)
      val folder: FileStorage = storage.files.directory("books").directory("audio")

      assertThat(folder.read("missing")).isNull()
      folder.delete("missing")

      assertThat(FileSystem.SYSTEM.exists(root / "app".encodeUtf8().hex())).isFalse()
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

      assertThat(app.files.directory("audio").read("track")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(app.cache.directory("audio").read("track")).isNotNull().isEqualTo(byteArrayOf(2))
      assertThat(user.cache.directory("audio").read("track")).isNotNull().isEqualTo(byteArrayOf(3))
      app.cache.directory("audio").delete("track")
      assertThat(app.cache.directory("audio").read("track")).isNull()
      assertThat(app.files.directory("audio").read("track")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(user.cache.directory("audio").read("track")).isNotNull().isEqualTo(byteArrayOf(3))
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

      assertThat(cache.read("track")).isNull()
      cache.delete("missing")
      assertThat(FileSystem.SYSTEM.exists(root / "cache")).isFalse()
      cache.write("track", byteArrayOf(3))
      assertThat(cache.read("track")).isNotNull().isEqualTo(byteArrayOf(3))
      assertThat(storage.files.read("track")).isNotNull().isEqualTo(byteArrayOf(1))
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

      assertThat(books.read("track")).isNull()
      assertThat(chapters.read("track")).isNull()
      assertThat(app.files.read("track")).isNotNull().isEqualTo(byteArrayOf(0))
      assertThat(app.files.directory("other").read("track")).isNotNull().isEqualTo(byteArrayOf(3))
      assertThat(app.cache.directory("books").read("track")).isNotNull().isEqualTo(byteArrayOf(4))
      assertThat(user.files.directory("books").read("track")).isNotNull().isEqualTo(byteArrayOf(5))
      assertThat(app.preferences("settings").data.first()[key]).isEqualTo("dark")
      chapters.write("track", byteArrayOf(6))
      assertThat(chapters.read("track")).isNotNull().isEqualTo(byteArrayOf(6))
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

      assertThat(app.files.read("track")).isNull()
      assertThat(app.files.directory("audio").read("track")).isNull()
      assertThat(app.cache.read("track")).isNotNull().isEqualTo(byteArrayOf(3))
      assertThat(app.cache.directory("audio").read("track")).isNotNull().isEqualTo(byteArrayOf(4))
      app.files.write("track", byteArrayOf(6))
      app.cache.deleteAll()
      assertThat(app.cache.read("track")).isNull()
      assertThat(app.cache.directory("audio").read("track")).isNull()
      assertThat(app.files.read("track")).isNotNull().isEqualTo(byteArrayOf(6))
      assertThat(user.files.read("track")).isNotNull().isEqualTo(byteArrayOf(5))
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

      assertThat(FileSystem.SYSTEM.exists(root / "app".encodeUtf8().hex())).isFalse()
      assertThat(FileSystem.SYSTEM.exists(root / "cache")).isFalse()
      missing.write("track", byteArrayOf(1))
      missing.deleteAll()
      missing.deleteAll()
      missing.write("track", byteArrayOf(2))
      assertThat(missing.read("track")).isNotNull().isEqualTo(byteArrayOf(2))
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

      assertThat(books.read("own")).isNull()
      assertThat(FileSystem.SYSTEM.exists(directoryLink)).isFalse()
      assertThat(FileSystem.SYSTEM.exists(fileLink)).isFalse()
      assertThat(privateFiles.read("track")).isNotNull().isEqualTo(byteArrayOf(2))
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

      assertFailure { app.files.read("linked") }.isInstanceOf<IllegalStateException>()
      assertFailure { app.files.write("linked", byteArrayOf(9)) }
        .isInstanceOf<IllegalStateException>()
      assertFailure { app.files.delete("linked") }.isInstanceOf<IllegalStateException>()
      assertThat(user.files.read("private")).isNotNull().isEqualTo(byteArrayOf(2))
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

      assertFailure { linked.path }.isInstanceOf<IllegalStateException>()
      assertFailure { linked.read("track") }.isInstanceOf<IllegalStateException>()
      assertFailure { linked.write("track", byteArrayOf(9)) }.isInstanceOf<IllegalStateException>()
      assertFailure { linked.delete("track") }.isInstanceOf<IllegalStateException>()
      assertFailure { linked.deleteAll() }.isInstanceOf<IllegalStateException>()
      assertFailure { app.files.directory("linked").deleteAll() }
        .isInstanceOf<IllegalStateException>()
      assertThat(privateFiles.read("track")).isNotNull().isEqualTo(byteArrayOf(2))
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
