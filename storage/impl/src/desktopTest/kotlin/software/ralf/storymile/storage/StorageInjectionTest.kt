@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.register
import software.ralf.app.platform.scope.runTestWithScope

class StorageInjectionTest {
  @Test
  fun `injected storage waits for scope entry and closes with its owner`() =
    runTestWithScope { scope ->
      val graph =
        createGraphFactory<Graph.Factory>()
          .create(InMemoryStoragePlatform(), scope.coroutineScope())
      val storage: Storage = graph.consumer.storage
      val directory: FileStorage = storage.files.directory("books")
      val cache: FileStorage = storage.cache.directory("covers")
      val writing = async { directory.write("chapter", byteArrayOf(1)) }
      runCurrent()

      assertFalse(writing.isCompleted)
      assertSame(storage, graph.storage)
      assertSame(storage, graph.consumer.storage)
      assertSame<Any>(storage, graph.scopedInstances.single())

      scope.register(graph.scopedInstances)

      writing.await()
      assertContentEquals(byteArrayOf(1), directory.read("chapter"))
      cache.write("cover", byteArrayOf(2))
      assertContentEquals(byteArrayOf(2), cache.read("cover"))
      val key = stringPreferencesKey("theme")
      val preferences = storage.preferences("settings")
      preferences.edit { it[key] = "dark" }
      assertThat(preferences.data.first()[key]).isEqualTo("dark")

      scope.destroy()

      assertFailsWith<CancellationException> { storage.files.directory("new") }
      assertFailsWith<CancellationException> { directory.read("chapter") }
      assertFailsWith<CancellationException> { directory.write("chapter", byteArrayOf(3)) }
      assertFailsWith<CancellationException> { directory.deleteAll() }
      assertFailsWith<CancellationException> { cache.read("cover") }
      assertFailsWith<CancellationException> { storage.preferences("settings") }
      assertFailsWith<CancellationException> { preferences.updateData { it } }
      assertFailsWith<CancellationException> { preferences.data.first() }
    }

  @Test
  fun `public factory creates storage owned by a separate scope`() = runTestWithScope { scope ->
    val graph =
      createGraphFactory<Graph.Factory>().create(InMemoryStoragePlatform(), scope.coroutineScope())
    val factory: ScopedStorage.Factory = graph.storageFactory
    val userScope = Scope.buildTestScope(this)
    val user: ScopedStorage = factory.create("user-alice", userScope.coroutineScope())
    scope.register(graph.scopedInstances)
    userScope.register(user)

    graph.storage.files.write("file", byteArrayOf(1))
    user.files.write("file", byteArrayOf(2))
    assertContentEquals(byteArrayOf(1), graph.storage.files.read("file"))
    assertContentEquals(byteArrayOf(2), user.files.read("file"))

    userScope.destroy()

    assertFailsWith<CancellationException> { user.files.read("file") }
    assertContentEquals(byteArrayOf(1), graph.storage.files.read("file"))
  }

  @Test
  fun `failed initialization leaves sibling injected storage usable`() = runTestWithScope { scope ->
    val platform = InMemoryStoragePlatform()
    val original: Storage =
      DefaultStorage("app", scope.coroutineScope(), platform).also {
        scope.register(it)
      }
    original.files.write("chapter", byteArrayOf(1))
    val otherScope = Scope.buildTestScope(this)
    val duplicate: Storage =
      DefaultStorage("app", otherScope.coroutineScope(), platform).also {
        otherScope.register(it)
      }

    val failure = assertFailsWith<IllegalStateException> { duplicate.files.read("chapter") }
    assertFalse(failure is CancellationException)

    val sibling: Storage =
      DefaultStorage("user-alice", otherScope.coroutineScope(), platform).also {
        otherScope.register(it)
      }
    sibling.files.write("chapter", byteArrayOf(2))
    assertContentEquals(byteArrayOf(1), original.files.read("chapter"))
    assertContentEquals(byteArrayOf(2), sibling.files.read("chapter"))
  }

  @DependencyGraph(AppScope::class, excludes = [DesktopStoragePlatform::class])
  interface Graph {
    val consumer: Consumer

    val storageFactory: ScopedStorage.Factory

    @ForScope(AppScope::class) val storage: Storage

    @ForScope(AppScope::class) val scopedInstances: Set<Scoped>

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides platform: StoragePlatform,
        @Provides @ForScope(AppScope::class) coroutineScope: CoroutineScope,
      ): Graph
    }
  }

  @Inject class Consumer(@ForScope(AppScope::class) val storage: Storage)
}
