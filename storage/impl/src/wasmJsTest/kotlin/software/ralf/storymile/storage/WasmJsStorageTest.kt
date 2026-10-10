package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.runTestWithScope

class WasmJsStorageTest {
  @Test
  fun `browser files preserve all byte values and empty replacements`() =
    runTestWithScope { scope ->
      val platform = WasmJsStoragePlatform()
      val storage: Storage = scope.storage("test-binary-${Random.nextInt(Int.MAX_VALUE)}", platform)
      val bytes = ByteArray(256) { it.toByte() }

      try {
        storage.files.write("audio.bin", bytes)
        assertContentEquals(bytes, storage.files.read("audio.bin"))

        storage.files.write("audio.bin", byteArrayOf())
        assertContentEquals(byteArrayOf(), storage.files.read("audio.bin"))
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
        assertNull(first.read("clip.bin"))
        first.delete("clip.bin")

        storage.files.write("clip.bin", byteArrayOf(1))
        first.write("clip.bin", byteArrayOf(2))
        second.write("clip.bin", byteArrayOf(3))

        assertContentEquals(byteArrayOf(1), storage.files.read("clip.bin"))
        assertContentEquals(
          byteArrayOf(2),
          storage.files.directory("stories").directory("audio").read("clip.bin"),
        )
        assertContentEquals(byteArrayOf(3), second.read("clip.bin"))

        first.delete("clip.bin")

        assertNull(first.read("clip.bin"))
        assertContentEquals(byteArrayOf(1), storage.files.read("clip.bin"))
        assertContentEquals(byteArrayOf(3), second.read("clip.bin"))
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
        assertNull(cache.read("clip.bin"))
        cache.delete("clip.bin")

        durable.write("clip.bin", byteArrayOf(1))
        cache.write("clip.bin", byteArrayOf(2))
        otherCache.write("clip.bin", byteArrayOf(3))

        assertContentEquals(byteArrayOf(1), durable.read("clip.bin"))
        assertContentEquals(byteArrayOf(2), cache.read("clip.bin"))
        assertContentEquals(byteArrayOf(3), otherCache.read("clip.bin"))
        assertNull(first.cache.read("clip.bin"))

        cache.delete("clip.bin")

        assertNull(cache.read("clip.bin"))
        assertContentEquals(byteArrayOf(1), durable.read("clip.bin"))
        assertContentEquals(byteArrayOf(3), otherCache.read("clip.bin"))
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

        assertNull(nested.read("clip.bin"))
        assertContentEquals(byteArrayOf(1), first.files.read("clip.bin"))
        assertContentEquals(byteArrayOf(3), sibling.read("clip.bin"))
        assertContentEquals(byteArrayOf(4), cache.read("clip.bin"))
        assertContentEquals(byteArrayOf(5), otherFiles.read("clip.bin"))
        assertEquals(7, preferences.data.first()[selected])

        nested.write("clip.bin", byteArrayOf(6))
        assertContentEquals(byteArrayOf(6), nested.read("clip.bin"))

        first.files.deleteAll()
        first.files.deleteAll()

        assertNull(first.files.read("clip.bin"))
        assertNull(nested.read("clip.bin"))
        assertNull(sibling.read("clip.bin"))
        assertContentEquals(byteArrayOf(4), cache.read("clip.bin"))
        assertContentEquals(byteArrayOf(5), otherFiles.read("clip.bin"))
        assertEquals(7, preferences.data.first()[selected])

        nested.write("clip.bin", byteArrayOf(8))
        assertContentEquals(
          byteArrayOf(8),
          first.files.directory("stories").directory("audio").read("clip.bin"),
        )
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
        assertNull(first.files.read("shared.bin"))
        assertNull(second.files.read("shared.bin"))

        first.files.write("shared.bin", byteArrayOf(0, -1, -128))
        second.files.write("shared.bin", byteArrayOf(42))

        assertContentEquals(byteArrayOf(0, -1, -128), first.files.read("shared.bin"))
        assertContentEquals(byteArrayOf(42), second.files.read("shared.bin"))

        first.files.delete("shared.bin")
        first.files.delete("shared.bin")

        assertNull(first.files.read("shared.bin"))
        assertContentEquals(byteArrayOf(42), second.files.read("shared.bin"))
      } finally {
        first.files.delete("shared.bin")
        second.files.delete("shared.bin")
        otherScope.destroy()
      }
    }
}
