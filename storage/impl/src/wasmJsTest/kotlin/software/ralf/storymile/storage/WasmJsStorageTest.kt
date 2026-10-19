package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.runTestWithScope

class WasmJsStorageTest {
  @Test
  fun `database filenames are stable and isolate user and database names`() =
    runTestWithScope { scope ->
      val platform = WasmJsStoragePlatform()
      val first: Storage = scope.storage(namespace = "user-alice", platform = platform)
      val second: Storage = scope.storage(namespace = "user-Alice", platform = platform)
      val path = first.databasePath("podcasts")

      assertThat(path).isNotNull().isEqualTo(first.databasePath("podcasts"))
      assertThat(setOf(path, first.databasePath("Podcasts"), second.databasePath("podcasts")).size)
        .isEqualTo(3)
    }

  @Test
  fun `browser files preserve all byte values and empty replacements`() =
    runTestWithScope { scope ->
      val platform = WasmJsStoragePlatform()
      val storage: Storage = scope.storage("test-binary-${Random.nextInt(Int.MAX_VALUE)}", platform)
      val bytes = ByteArray(256) { it.toByte() }

      try {
        storage.files.write("audio.bin", bytes)
        assertThat(storage.files.read("audio.bin")).isNotNull().isEqualTo(bytes)

        storage.files.write("audio.bin", byteArrayOf())
        assertThat(storage.files.read("audio.bin")).isNotNull().isEqualTo(byteArrayOf())
      } finally {
        storage.files.delete("audio.bin")
      }
    }

  @Test
  fun `browser directories isolate nested files and preserve their paths`() =
    runTestWithScope { scope ->
      val platform = WasmJsStoragePlatform()
      val storage: Storage =
        scope.storage("test-folders-${Random.nextInt(Int.MAX_VALUE)}", platform)
      val first: FileStorage = storage.files.directory("stories").directory("audio")
      val second: FileStorage = storage.files.directory("bookmarks").directory("audio")

      try {
        assertThat(first.read("clip.bin")).isNull()
        first.delete("clip.bin")

        storage.files.write("clip.bin", byteArrayOf(1))
        first.write("clip.bin", byteArrayOf(2))
        second.write("clip.bin", byteArrayOf(3))

        assertThat(storage.files.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(1))
        assertThat(storage.files.directory("stories").directory("audio").read("clip.bin"))
          .isNotNull()
          .isEqualTo(byteArrayOf(2))
        assertThat(second.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(3))

        first.delete("clip.bin")

        assertThat(first.read("clip.bin")).isNull()
        assertThat(storage.files.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(1))
        assertThat(second.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(3))
      } finally {
        storage.files.delete("clip.bin")
        first.delete("clip.bin")
        second.delete("clip.bin")
      }
    }

  @Test
  fun `browser cache stays separate from durable files and other scopes`() =
    runTestWithScope { scope ->
      val platform = WasmJsStoragePlatform()
      val otherScope = Scope.buildTestScope(this, name = scope.name)
      val suffix = Random.nextInt(Int.MAX_VALUE)
      val first: Storage = scope.storage("test-cache-first-$suffix", platform)
      val second: Storage = otherScope.storage("test-cache-second-$suffix", platform)
      val durable: FileStorage = first.files.directory("stories").directory("audio")
      val cache: FileStorage = first.cache.directory("stories").directory("audio")
      val otherCache: FileStorage = second.cache.directory("stories").directory("audio")

      try {
        assertThat(cache.read("clip.bin")).isNull()
        cache.delete("clip.bin")

        durable.write("clip.bin", byteArrayOf(1))
        cache.write("clip.bin", byteArrayOf(2))
        otherCache.write("clip.bin", byteArrayOf(3))

        assertThat(durable.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(1))
        assertThat(cache.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(2))
        assertThat(otherCache.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(3))
        assertThat(first.cache.read("clip.bin")).isNull()

        cache.delete("clip.bin")

        assertThat(cache.read("clip.bin")).isNull()
        assertThat(durable.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(1))
        assertThat(otherCache.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(3))
      } finally {
        durable.delete("clip.bin")
        cache.delete("clip.bin")
        otherCache.delete("clip.bin")
        otherScope.destroy()
      }
    }

  @Test
  fun `browser clearing removes only the selected subtree and handles remain usable`() =
    runTestWithScope { scope ->
      val platform = WasmJsStoragePlatform()
      val otherScope = Scope.buildTestScope(this, name = scope.name)
      val suffix = Random.nextInt(Int.MAX_VALUE)
      val first: Storage = scope.storage("test-clear-first-$suffix", platform)
      val second: Storage = otherScope.storage("test-clear-second-$suffix", platform)
      val subtree: FileStorage = first.files.directory("stories")
      val nested: FileStorage = subtree.directory("audio")
      val sibling: FileStorage = first.files.directory("bookmarks")
      val cache: FileStorage = first.cache.directory("stories").directory("audio")
      val otherFiles: FileStorage = second.files.directory("stories").directory("audio")
      val preferences = first.preferences("settings")
      val selected = intPreferencesKey("selected")

      try {
        subtree.deleteAll()
        first.files.write("clip.bin", byteArrayOf(1))
        nested.write("clip.bin", byteArrayOf(2))
        sibling.write("clip.bin", byteArrayOf(3))
        cache.write("clip.bin", byteArrayOf(4))
        otherFiles.write("clip.bin", byteArrayOf(5))
        preferences.edit { it[selected] = 7 }

        subtree.deleteAll()

        assertThat(nested.read("clip.bin")).isNull()
        assertThat(first.files.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(1))
        assertThat(sibling.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(3))
        assertThat(cache.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(4))
        assertThat(otherFiles.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(5))
        assertThat(preferences.data.first()[selected]).isEqualTo(7)

        nested.write("clip.bin", byteArrayOf(6))
        assertThat(nested.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(6))

        first.files.deleteAll()
        first.files.deleteAll()

        assertThat(first.files.read("clip.bin")).isNull()
        assertThat(nested.read("clip.bin")).isNull()
        assertThat(sibling.read("clip.bin")).isNull()
        assertThat(cache.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(4))
        assertThat(otherFiles.read("clip.bin")).isNotNull().isEqualTo(byteArrayOf(5))
        assertThat(preferences.data.first()[selected]).isEqualTo(7)

        nested.write("clip.bin", byteArrayOf(8))
        assertThat(first.files.directory("stories").directory("audio").read("clip.bin"))
          .isNotNull()
          .isEqualTo(byteArrayOf(8))
      } finally {
        first.files.deleteAll()
        first.cache.deleteAll()
        second.files.deleteAll()
        preferences.edit { it.clear() }
        otherScope.destroy()
      }
    }

  @Test
  fun `browser files with the same name stay isolated across same-name scopes`() =
    runTestWithScope { scope ->
      val platform = WasmJsStoragePlatform()
      val otherScope = Scope.buildTestScope(this, name = scope.name)
      val suffix = Random.nextInt(Int.MAX_VALUE)
      val first: Storage = scope.storage("test-first-$suffix", platform)
      val second: Storage = otherScope.storage("test-second-$suffix", platform)

      try {
        first.files.delete("shared.bin")
        assertThat(first.files.read("shared.bin")).isNull()
        assertThat(second.files.read("shared.bin")).isNull()

        first.files.write("shared.bin", byteArrayOf(0, -1, -128))
        second.files.write("shared.bin", byteArrayOf(42))

        assertThat(first.files.read("shared.bin")).isNotNull().isEqualTo(byteArrayOf(0, -1, -128))
        assertThat(second.files.read("shared.bin")).isNotNull().isEqualTo(byteArrayOf(42))

        first.files.delete("shared.bin")
        first.files.delete("shared.bin")

        assertThat(first.files.read("shared.bin")).isNull()
        assertThat(second.files.read("shared.bin")).isNotNull().isEqualTo(byteArrayOf(42))
      } finally {
        first.files.delete("shared.bin")
        second.files.delete("shared.bin")
        otherScope.destroy()
      }
    }
}
