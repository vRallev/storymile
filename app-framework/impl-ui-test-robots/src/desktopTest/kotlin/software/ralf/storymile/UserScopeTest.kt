package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
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
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.login.Login
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.InMemoryStorageFactory
import software.ralf.storymile.storage.RuntimeModeStorageFactory
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.user.SubscriptionTier
import software.ralf.storymile.user.User
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class UserScopeTest {
  @Test
  fun `signing in and out closes the old scope and preserves separate libraries`() = runTest {
    withApplication { graph ->
      val scopes = graph.userManager.userScope
      val guest = scopes.value
      guest.metroDependencyGraph<UserBindings>().storage.files.write("episode", byteArrayOf(1))
      val work = guest.launch { awaitCancellation() }

      graph.login.signIn()
      val account = scopes.first { it !== guest }
      val accountBindings = account.metroDependencyGraph<UserBindings>()
      assertThat(guest.isDestroyed()).isTrue()
      assertThat(work.isCancelled).isTrue()
      assertThat(accountBindings.user.isGuest).isFalse()
      assertThat(accountBindings.user.subscriptionTier).isEqualTo(SubscriptionTier.BASIC)
      assertThat(accountBindings.storage.files.read("episode")).isNull()
      accountBindings.storage.files.write("episode", byteArrayOf(2))

      graph.login.signOut()
      val nextGuest = scopes.first { it !== account }
      assertThat(account.isDestroyed()).isTrue()
      assertThat(nextGuest.metroDependencyGraph<UserBindings>().storage.files.read("episode"))
        .isNotNull()
        .isEqualTo(byteArrayOf(1))

      graph.login.signIn()
      val nextAccount = scopes.first { it !== nextGuest }
      assertThat(nextAccount.metroDependencyGraph<UserBindings>().storage.files.read("episode"))
        .isNotNull()
        .isEqualTo(byteArrayOf(2))
    }
  }

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
      val userScope = manager.userScope.first()
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
      val firstScope = firstManager.userScope.value
      val first: User = firstScope.metroDependencyGraph<UserBindings>().user
      val second = withApplication { secondGraph ->
        val secondManager: UserManager = secondGraph.userManager
        val second: User = secondManager.userScope.value.metroDependencyGraph<UserBindings>().user
        assertThat(second).isNotSameInstanceAs(first)
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
        graph.userManager.userScope.value.metroDependencyGraph<UserBindings>().storage
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
      val userScope = manager.userScope.value
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
        val userGraph = manager.userScope.value.metroDependencyGraph<UserBindings>()
        val storage: Storage = userGraph.storage
        storage.files.write("chapter", byteArrayOf(1))
        storage.preferences("settings").edit { it[key] = "guest" }
        userGraph.user.id
      }

    withApplication(factory) { graph ->
      val manager: UserManager = graph.userManager
      val userGraph = manager.userScope.value.metroDependencyGraph<UserBindings>()
      val storage: Storage = userGraph.storage

      assertThat(userGraph.user.id).isEqualTo(userId)
      assertThat(storage.files.read("chapter")).isNotNull().isEqualTo(byteArrayOf(1))
      assertThat(storage.preferences("settings").data.first()[key]).isEqualTo("guest")
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
    try {
      return block(application.rootScope.metroDependencyGraph<Graph>())
    } finally {
      application.destroy()
    }
  }

  @DependencyGraph(
    AppScope::class,
    excludes =
      [DefaultStorage.Factory::class, RuntimeModeStorageFactory::class, TestStorageBindings::class],
  )
  interface Graph : AppGraph {
    val userManager: UserManager
    val login: Login

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

    @ForScope(UserScope::class) val storage: Storage
  }
}
