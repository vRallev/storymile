package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.podcast.FakePodcasts
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.InMemoryStorageFactory
import software.ralf.storymile.storage.OkioStoragePlatform
import software.ralf.storymile.storage.RuntimeModeStorageFactory
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.user.SubscriptionTier
import software.ralf.storymile.user.User
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class UserScopeTest {
  @Test
  fun `application starts with a guest bound to its user scope`() = runTest {
    val application = KmpApplication {
      val graph = createGraphFactory<Graph.Factory>().create(it, InMemoryStorageFactory())
      assertFailure { graph.userManager.userScope }.isInstanceOf<IllegalStateException>()
      graph
    }
    try {
      application.awaitReady()
      val manager: UserManager = application.rootScope.metroDependencyGraph<Graph>().userManager
      val userScope = checkNotNull(manager.userScope.first())
      val user: User = userScope.metroDependencyGraph<UserBindings>().user
      assertThat(user.isGuest).isTrue()
      assertThat(user.subscriptionTier).isEqualTo(SubscriptionTier.BASIC)
      assertThat(manager.userScope.value).isSameInstanceAs(userScope)
      assertThat(userScope.isDestroyed()).isFalse()
    } finally {
      application.destroy()
    }
  }

  @Test
  fun `each application owns a separate guest and scope`() = runTest {
    withApplication { firstGraph ->
      val firstManager: UserManager = firstGraph.userManager
      val firstScope = checkNotNull(firstManager.userScope.value)
      val first: User = firstScope.metroDependencyGraph<UserBindings>().user
      val second = withApplication { secondGraph ->
        val secondManager: UserManager = secondGraph.userManager
        val second: User =
          checkNotNull(secondManager.userScope.value).metroDependencyGraph<UserBindings>().user
        assertThat(second.storageId).isNotEqualTo(first.storageId)
        assertThat(secondManager.userScope.value).isNotSameInstanceAs(firstScope)
        assertThat(firstManager.userScope.value).isSameInstanceAs(firstScope)
        secondManager
      }

      assertFailure { second.userScope }.isInstanceOf<IllegalStateException>()
      assertThat(firstManager.userScope.value).isSameInstanceAs(firstScope)
      assertThat(firstScope.isDestroyed()).isFalse()
      val storage: Storage = firstScope.metroDependencyGraph<UserBindings>().storage
      storage.files.write("chapter", byteArrayOf(1))
      assertThat(storage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
    }
  }

  @Test
  fun `guest data is separate from application data`() = runTest {
    withApplication { graph ->
      val appStorage: Storage = graph.storage
      val userStorage: Storage =
        checkNotNull(graph.userManager.userScope.value).metroDependencyGraph<UserBindings>().storage
      val key = stringPreferencesKey("value")

      appStorage.files.write("chapter", byteArrayOf(1))
      userStorage.files.write("chapter", byteArrayOf(2))
      appStorage.preferences("settings").edit { it[key] = "app" }
      userStorage.preferences("settings").edit { it[key] = "guest" }

      assertThat(appStorage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(userStorage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(2))
      assertThat(appStorage.preferences("settings").data.first()[key]).isEqualTo("app")
      assertThat(userStorage.preferences("settings").data.first()[key]).isEqualTo("guest")
    }
  }

  @Test
  fun `application teardown cancels user work and closes user storage`() = runTest {
    val application = createApplication()
    val rootScope = application.rootScope
    try {
      val manager: UserManager = rootScope.metroDependencyGraph<Graph>().userManager
      val userScope = checkNotNull(manager.userScope.value)
      val storage: Storage = userScope.metroDependencyGraph<UserBindings>().storage
      val preferences = storage.preferences("settings")
      storage.files.write("chapter", byteArrayOf(1))
      val work = userScope.launch { awaitCancellation() }
      assertThat(work.isActive).isTrue()

      application.destroy()

      assertThat(userScope.isDestroyed()).isTrue()
      assertThat(work.isCancelled).isTrue()
      assertFailure { manager.userScope }.isInstanceOf<IllegalStateException>()
      assertFailure { storage.files.read("chapter") }.isInstanceOf<CancellationException>()
      assertFailure { preferences.data.first() }.isInstanceOf<CancellationException>()
    } finally {
      if (!rootScope.isDestroyed()) {
        application.destroy()
      }
    }
  }

  @Test
  fun `guest data remains available after application recreation`() = runTest {
    val factory: Storage.Factory = InMemoryStorageFactory()
    val key = stringPreferencesKey("value")
    val userId =
      withApplication(factory) { graph ->
        val manager: UserManager = graph.userManager
        val userGraph = checkNotNull(manager.userScope.value).metroDependencyGraph<UserBindings>()
        val storage: Storage = userGraph.storage
        storage.files.write("chapter", byteArrayOf(1))
        storage.preferences("settings").edit { it[key] = "guest" }
        userGraph.user.storageId
      }

    withApplication(factory) { graph ->
      val manager: UserManager = graph.userManager
      val userGraph = checkNotNull(manager.userScope.value).metroDependencyGraph<UserBindings>()
      val storage: Storage = userGraph.storage

      assertThat(userGraph.user.storageId).isEqualTo(userId)
      assertThat(storage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(storage.preferences("settings").data.first()[key]).isEqualTo("guest")
    }
  }

  @Test
  fun `sign-in and keep preserve the guest queue while wipe keeps only preferences`() = runTest {
    val root = Files.createTempDirectory("storymile-session").toString().toPath()
    val platform =
      object : OkioStoragePlatform(FileSystem.SYSTEM, Dispatchers.IO) {
        override fun rootDirectory(): Path = root / "data"

        override fun cacheDirectory(): Path = root / "cache"
      }
    val disk = DefaultStorage.Factory(platform)
    val allowDelete = CompletableDeferred<Unit>()
    val deleted = CompletableDeferred<String>()
    val storage =
      object : Storage.Factory by disk {
        override suspend fun deleteNamespace(namespace: String) {
          allowDelete.await()
          disk.deleteNamespace(namespace)
          deleted.complete(namespace)
        }
      }
    val pref = stringPreferencesKey("speed")
    val episode = FakePodcasts.queue.first().copy(feedUrl = "https://example.test/local-episode")
    try {
      val initialId =
        withApplication(storage) { graph ->
          val manager = graph.userManager
          val scope = checkNotNull(manager.userScope.value)
          val user = scope.metroDependencyGraph<UserBindings>()
          user.storage.preferences("playback").edit { it[pref] = "1.5" }
          user.storage.files.write("audio", byteArrayOf(1))
          user.storage.cache.write("art", byteArrayOf(2))
          user.queue.save(episode)
          user.queue.episodes.filterNotNull().first { episode in it }
          val scopedUser = user.user
          val id = scopedUser.storageId
          manager.signIn()
          assertThat(manager.user.value.storageId).isEqualTo(id)
          assertThat(manager.userScope.value).isSameInstanceAs(scope)
          assertThat(manager.user.value.isGuest).isFalse()
          assertThat(scopedUser.storageId).isEqualTo(id)
          assertThat(scopedUser.isGuest).isFalse()
          manager.signOut(keepData = true)
          assertThat(manager.user.value.storageId).isEqualTo(id)
          assertThat(manager.userScope.value).isSameInstanceAs(scope)
          assertThat(manager.user.value.isGuest).isTrue()
          assertThat(scopedUser.storageId).isEqualTo(id)
          assertThat(scopedUser.isGuest).isTrue()
          manager.signIn()
          id
        }
      val newId =
        withApplication(storage) { graph ->
          val manager = graph.userManager
          assertThat(manager.user.value.isGuest).isFalse()
          assertThat(manager.user.value.storageId).isEqualTo(initialId)
          val previous = checkNotNull(manager.userScope.value)
          val before = previous.metroDependencyGraph<UserBindings>()
          assertThat(episode in before.queue.episodes.filterNotNull().first()).isTrue()
          assertThat(before.storage.files.read("audio")).isNotNull()
          manager.signOut(keepData = false)
          assertThat(manager.user.value.isGuest).isTrue()
          assertThat(manager.user.value.storageId).isNotEqualTo(initialId)
          assertThat(previous.isDestroyed()).isTrue()
          val after = checkNotNull(manager.userScope.value).metroDependencyGraph<UserBindings>()
          assertThat(episode in after.queue.episodes.filterNotNull().first()).isFalse()
          assertThat(after.storage.files.read("audio")).isNull()
          assertThat(after.storage.cache.read("art")).isNull()
          assertThat(after.storage.preferences("playback").data.first()[pref]).isEqualTo("1.5")
          // Close the app while deletion is pending. The next launch must resume the wipe.
          manager.user.value.storageId
        }
      allowDelete.complete(Unit)
      withApplication(storage) { graph ->
        assertThat(graph.userManager.user.value.storageId).isEqualTo(newId)
        assertThat(deleted.await()).isEqualTo("user-$initialId")
        assertThat(disk.hasNamespace("user-$initialId")).isFalse()
      }
    } finally {
      allowDelete.complete(Unit)
      FileSystem.SYSTEM.deleteRecursively(root)
    }
  }

  @Test
  fun `failed preference copy restores the signed-in profile and its files`() = runTest {
    val factory =
      object : Storage.Factory by InMemoryStorageFactory() {
        override suspend fun copyPreferences(sourceNamespace: String, targetNamespace: String) {
          error("Could not copy preferences")
        }
      }
    withApplication(factory) { graph ->
      val manager = graph.userManager
      manager.signIn()
      val original = manager.user.value
      val before =
        checkNotNull(manager.userScope.value).metroDependencyGraph<UserBindings>().storage
      before.files.write("episode", byteArrayOf(1))
      assertFailure { manager.signOut(keepData = false) }.isInstanceOf<IllegalStateException>()
      assertThat(manager.user.value).isEqualTo(original)
      val restored =
        checkNotNull(manager.userScope.value).metroDependencyGraph<UserBindings>().storage
      assertThat(restored.files.read("episode")).isNotNull().isEqualTo(byteArrayOf(1))
    }
  }

  private suspend fun createApplication(
    storageFactory: Storage.Factory = InMemoryStorageFactory(),
  ): KmpApplication {
    val application = KmpApplication {
      createGraphFactory<Graph.Factory>().create(it, storageFactory)
    }
    application.awaitReady()
    return application
  }

  private suspend fun <T> withApplication(
    storageFactory: Storage.Factory = InMemoryStorageFactory(),
    block: suspend (Graph) -> T,
  ): T {
    val application = createApplication(storageFactory)
    val graph = application.rootScope.metroDependencyGraph<Graph>()
    try {
      return block(graph)
    } finally {
      application.destroy()
      graph.appScopeCoroutineScopeScoped.coroutineContext.job.join()
    }
  }

  @DependencyGraph(
    AppScope::class,
    excludes =
      [DefaultStorage.Factory::class, RuntimeModeStorageFactory::class, TestStorageBindings::class],
  )
  interface Graph : AppGraph {

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides rootScopeProvider: RootScopeProvider,
        @Provides storageFactory: Storage.Factory,
      ): Graph
    }
  }

  @ContributesTo(UserScope::class)
  interface UserBindings {
    val user: User

    val queue: QueueRepository

    @ForScope(UserScope::class) val storage: Storage
  }
}
