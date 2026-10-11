package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.InMemoryStorageFactory
import software.ralf.storymile.storage.ScopedStorage
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.user.User
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class UserScopeTest {
  @Test
  fun `application starts with a guest bound to its user scope`() = runTest {
    withApplication { graph ->
      val manager: UserManager = graph.userManager
      val userGraph = manager.userScope.metroDependencyGraph<UserBindings>()

      assertThat(manager.user.isGuest).isTrue()
      assertThat(userGraph.user).isSameInstanceAs(manager.user)
      assertThat(manager.userScope.isDestroyed()).isFalse()
    }
  }

  @Test
  fun `guest data is separate from application data`() = runTest {
    withApplication { graph ->
      val appStorage: Storage = graph.storage
      val userStorage: Storage =
        graph.userManager.userScope.metroDependencyGraph<UserBindings>().storage
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
      val userScope = manager.userScope
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
    val factory: ScopedStorage.Factory = InMemoryStorageFactory()
    val key = stringPreferencesKey("value")
    val userId =
      withApplication(factory) { graph ->
        val manager: UserManager = graph.userManager
        val storage: Storage = manager.userScope.metroDependencyGraph<UserBindings>().storage
        storage.files.write("chapter", byteArrayOf(1))
        storage.preferences("settings").edit { it[key] = "guest" }
        manager.user.id
      }

    withApplication(factory) { graph ->
      val manager: UserManager = graph.userManager
      val storage: Storage = manager.userScope.metroDependencyGraph<UserBindings>().storage

      assertThat(manager.user.id).isEqualTo(userId)
      assertThat(storage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(storage.preferences("settings").data.first()[key]).isEqualTo("guest")
    }
  }

  private fun createApplication(
    storageFactory: ScopedStorage.Factory = InMemoryStorageFactory(),
  ): KmpApplication {
    val application = KmpApplication()
    application.create(
      createGraphFactory<Graph.Factory>().create(application, storageFactory),
    )
    return application
  }

  private suspend fun <T> withApplication(
    storageFactory: ScopedStorage.Factory = InMemoryStorageFactory(),
    block: suspend (Graph) -> T,
  ): T {
    val application = createApplication(storageFactory)
    try {
      return block(application.rootScope.metroDependencyGraph<Graph>())
    } finally {
      application.destroy()
    }
  }

  @DependencyGraph(
    AppScope::class,
    excludes = [DefaultStorage.Factory::class, TestStorageBindings::class],
  )
  interface Graph : AppGraph {
    val userManager: UserManager

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides rootScopeProvider: RootScopeProvider,
        @Provides storageFactory: ScopedStorage.Factory,
      ): Graph
    }
  }

  @ContributesTo(UserScope::class)
  interface UserBindings {
    val user: User

    @ForScope(UserScope::class) val storage: Storage
  }
}
