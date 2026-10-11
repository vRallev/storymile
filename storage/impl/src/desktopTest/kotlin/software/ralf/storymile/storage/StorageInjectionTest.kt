@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
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

      assertThat(writing.isCompleted).isFalse()
      assertThat(graph.storage).isSameInstanceAs(storage)
      assertThat(graph.consumer.storage).isSameInstanceAs(storage)
      assertThat(graph.scopedInstances.single()).isSameInstanceAs(storage)

      scope.register(graph.scopedInstances)

      writing.await()
      assertThat(directory.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
      cache.write("cover", byteArrayOf(2))
      assertThat(cache.read("cover")).isNotNull().isEqualTo(byteArrayOf(2))
      val key = stringPreferencesKey("theme")
      val preferences = storage.preferences("settings")
      preferences.edit { it[key] = "dark" }
      assertThat(preferences.data.first()[key]).isEqualTo("dark")

      scope.destroy()

      assertFailure { storage.files.directory("new") }.isInstanceOf<CancellationException>()
      assertFailure { directory.read("chapter") }.isInstanceOf<CancellationException>()
      assertFailure { directory.write("chapter", byteArrayOf(3)) }
        .isInstanceOf<CancellationException>()
      assertFailure { directory.deleteAll() }.isInstanceOf<CancellationException>()
      assertFailure { cache.read("cover") }.isInstanceOf<CancellationException>()
      assertFailure { storage.preferences("settings") }.isInstanceOf<CancellationException>()
      assertFailure { preferences.updateData { it } }.isInstanceOf<CancellationException>()
      assertFailure { preferences.data.first() }.isInstanceOf<CancellationException>()
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
    assertThat(graph.storage.files.read("file")).isNotNull().isEqualTo(byteArrayOf(1))
    assertThat(user.files.read("file")).isNotNull().isEqualTo(byteArrayOf(2))

    userScope.destroy()

    assertFailure { user.files.read("file") }.isInstanceOf<CancellationException>()
    assertThat(graph.storage.files.read("file")).isNotNull().isEqualTo(byteArrayOf(1))
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

    assertFailure {
      duplicate.files.read("chapter")
    }
      .isInstanceOf<IllegalStateException>()
      .isNotInstanceOf<CancellationException>()

    val sibling: Storage =
      DefaultStorage("user-alice", otherScope.coroutineScope(), platform).also {
        otherScope.register(it)
      }
    sibling.files.write("chapter", byteArrayOf(2))
    assertThat(original.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
    assertThat(sibling.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(2))
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
