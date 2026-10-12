package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.playlist.PlaylistRepository
import software.ralf.storymile.restart.ApplicationRestarter
import software.ralf.storymile.restart.DefaultApplicationRestarter
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.TestRuntimeModeController
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.DesktopStoragePlatform
import software.ralf.storymile.storage.InMemoryStoragePlatform
import software.ralf.storymile.storage.ScopedStorage
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.storage.StoragePlatform
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class RuntimeModeStorageTest {
  @Test
  fun `fake playlist resets after restart without changing saved real playlists`() = runTest {
    val platform = InMemoryStoragePlatform()
    seedStorage(platform)
    val saved =
      """{"version":1,"playlists":[{"id":"saved","name":"My saved playlist","episodeIds":["real-episode"]}]}"""
    withRawStorage(platform, "user-guest") {
      it.files.directory("playlists").write("playlists.json", saved.encodeToByteArray())
    }

    withApplication(platform) { graph, _ ->
      val repository = graph.playlistRepository()
      val playlist = repository.playlists.first { it.isNotEmpty() }.single()
      assertThat(playlist.name.value).isEqualTo("Storymile test episodes")
      assertThat(playlist.episodeIds.map { it.value })
        .isEqualTo(listOf("test-episode-1", "test-episode-2", "test-episode-3"))
      for (episode in playlist.episodeIds) {
        val resource =
          "composeResources/software.ralf.storymile.playback.impl.generated.resources/files/test-episodes/${episode.value}.mp3"
        val recording =
          checkNotNull(javaClass.classLoader.getResourceAsStream(resource)).use { it.readBytes() }
        assertThat(recording.size > 1024).isTrue()
      }
      repository.delete(playlist.id)
      repository.playlists.first { it.isEmpty() }
    }

    withApplication(platform) { graph, _ ->
      val playlist = graph.playlistRepository().playlists.first { it.isNotEmpty() }.single()
      assertThat(playlist.name.value).isEqualTo("Storymile test episodes")
      assertThat(playlist.episodeIds.map { it.value })
        .isEqualTo(listOf("test-episode-1", "test-episode-2", "test-episode-3"))
      graph.runtimeModeController.switchMode(RuntimeMode.Real)
    }

    withApplication(platform) { graph, _ ->
      val playlist = graph.playlistRepository().playlists.first { it.isNotEmpty() }.single()
      assertThat(playlist.name.value).isEqualTo("My saved playlist")
      assertThat(playlist.episodeIds.map { it.value }).isEqualTo(listOf("real-episode"))
    }
  }

  @Test
  fun `fake app and user storage start empty and reset after application recreation`() = runTest {
    val platform = InMemoryStoragePlatform()
    seedStorage(platform)

    withApplication(platform) { graph, restarter ->
      assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Fake)
      graph.storage.assertData(null)
      graph.userStorage().assertData(null)

      graph.storage.writeData("fake app")
      graph.userStorage().writeData("fake guest")

      graph.storage.assertData("fake app")
      graph.userStorage().assertData("fake guest")
      assertThat(restarter.restartCount).isEqualTo(0)
    }

    withRawStorage(platform, "app") { it.assertData("saved app") }
    withRawStorage(platform, "user-guest") { it.assertData("saved guest") }

    withApplication(platform) { graph, restarter ->
      assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Fake)
      graph.storage.assertData(null)
      graph.userStorage().assertData(null)
      assertThat(restarter.restartCount).isEqualTo(0)
    }
  }

  @Test
  fun `switching to real preserves saved data and keeps fake storage until restart`() = runTest {
    val platform = InMemoryStoragePlatform()
    seedStorage(platform)

    withApplication(platform) { graph, restarter ->
      graph.runtimeModeController.switchMode(RuntimeMode.Real)

      assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Fake)
      assertThat(restarter.restartCount).isEqualTo(1)
      graph.storage.writeData("outgoing fake app")
      graph.userStorage().writeData("outgoing fake guest")
    }

    withApplication(platform) { graph, restarter ->
      assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Real)
      graph.storage.assertData("saved app")
      graph.userStorage().assertData("saved guest")
      graph.storage.writeData("updated app")
      graph.userStorage().writeData("updated guest")
      assertThat(restarter.restartCount).isEqualTo(0)
    }

    withApplication(platform) { graph, _ ->
      assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Real)
      graph.storage.assertData("updated app")
      graph.userStorage().assertData("updated guest")
    }
  }

  private suspend fun TestScope.seedStorage(platform: StoragePlatform) {
    withRawStorage(platform, "app") { it.writeData("saved app") }
    withRawStorage(platform, "runtime-mode") { storage ->
      storage.preferences("runtime_mode").edit {
        it[stringPreferencesKey("runtime_mode")] = RuntimeMode.Fake.name
      }
    }
    withRawStorage(platform, "user-guest") { it.writeData("saved guest") }
  }

  private suspend fun TestScope.withRawStorage(
    platform: StoragePlatform,
    namespace: String,
    block: suspend (Storage) -> Unit,
  ) {
    val scope = Scope.buildTestScope(this)
    val storage: ScopedStorage = DefaultStorage(namespace, scope.coroutineScope(), platform)
    scope.register(storage)
    try {
      block(storage)
    } finally {
      scope.destroy()
    }
  }

  private suspend fun withApplication(
    platform: StoragePlatform,
    block: suspend (Graph, TestApplicationRestarter) -> Unit,
  ) {
    val application = KmpApplication()
    val restarter = TestApplicationRestarter()
    val graph = createGraphFactory<Graph.Factory>().create(application, platform, restarter)
    application.create(graph)
    try {
      block(graph, restarter)
    } finally {
      application.destroy()
    }
  }

  private fun Graph.userStorage(): Storage =
    userManager.userScope.value.metroDependencyGraph<UserStorageBindings>().storage

  private fun Graph.playlistRepository(): PlaylistRepository =
    userManager.userScope.value.metroDependencyGraph<UserStorageBindings>().playlistRepository

  private suspend fun Storage.writeData(value: String) {
    preferences("settings").edit { it[stringPreferencesKey("value")] = value }
    files.directory("nested").write("value", "$value-file".encodeToByteArray())
    cache.directory("nested").write("value", "$value-cache".encodeToByteArray())
  }

  private suspend fun Storage.assertData(value: String?) {
    assertThat(preferences("settings").data.first()[stringPreferencesKey("value")]).isEqualTo(value)
    assertThat(files.directory("nested").read("value")?.decodeToString())
      .isEqualTo(value?.let { "$it-file" })
    assertThat(cache.directory("nested").read("value")?.decodeToString())
      .isEqualTo(value?.let { "$it-cache" })
  }

  @DependencyGraph(
    AppScope::class,
    excludes =
      [
        DesktopStoragePlatform::class,
        TestStorageBindings::class,
        TestRuntimeModeController::class,
        DefaultApplicationRestarter::class,
      ],
  )
  interface Graph : AppGraph {
    val runtimeModeController: RuntimeModeController

    val userManager: UserManager

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides rootScopeProvider: RootScopeProvider,
        @Provides storagePlatform: StoragePlatform,
        @Provides restarter: ApplicationRestarter,
      ): Graph
    }
  }

  @ContributesTo(UserScope::class)
  interface UserStorageBindings {
    @ForScope(UserScope::class) val storage: Storage

    val playlistRepository: PlaylistRepository
  }

  private class TestApplicationRestarter : ApplicationRestarter {
    var restartCount = 0
      private set

    override fun restart() {
      restartCount++
    }
  }
}
