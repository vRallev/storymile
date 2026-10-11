package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.compose.test
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.approot.AppRootPresenter
import software.ralf.storymile.playlist.EpisodeId
import software.ralf.storymile.playlist.PlaylistName
import software.ralf.storymile.playlist.PlaylistRepository
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.InMemoryStorageFactory
import software.ralf.storymile.storage.ScopedStorage
import software.ralf.storymile.templates.AppTemplate
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class PlaylistScopeTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `playback is available only while at least one playlist contains episodes`() = runTest {
    withApplication { graph ->
      val repository: PlaylistRepository = graph.playlistRepository()
      graph.appRootPresenter.test(this) {
        awaitItem().assertPlayback(visible = false)

        repository.create(PlaylistName("First"))
        val first = repository.playlists.first { it.size == 1 }.first().id
        runCurrent()
        expectNoEvents()

        val episode = EpisodeId("episode")
        repository.addEpisode(first, episode)
        awaitItem().assertPlayback(visible = true)

        repository.create(PlaylistName("Second"))
        val second = repository.playlists.first { it.size == 2 }.last().id
        runCurrent()
        expectNoEvents()
        repository.addEpisode(second, episode)
        repository.playlists.first { it.last().episodeIds.isNotEmpty() }
        runCurrent()
        expectNoEvents()

        repository.removeEpisode(first, episode)
        repository.playlists.first { it.first().episodeIds.isEmpty() }
        runCurrent()
        expectNoEvents()
        repository.delete(second)
        awaitItem().assertPlayback(visible = false)

        repository.addEpisode(first, episode)
        awaitItem().assertPlayback(visible = true)
        repository.removeEpisode(first, episode)
        awaitItem().assertPlayback(visible = false)
      }
    }
  }

  private fun AppTemplate.assertPlayback(visible: Boolean) {
    assertThat(this).isInstanceOf<AppTemplate.AdaptiveTemplate>().given {
      if (visible) {
        assertThat(it.playback).isNotNull()
        assertThat(it.expandedPlayback).isNotNull()
      } else {
        assertThat(it.playback).isNull()
        assertThat(it.expandedPlayback).isNull()
      }
    }
  }

  @Test
  fun `each guest scope owns one repository with separate playlists`() = runTest {
    withApplication { firstGraph ->
      val first: PlaylistRepository = firstGraph.playlistRepository()
      assertThat(firstGraph.playlistRepository()).isSameInstanceAs(first)
      first.create(PlaylistName("First guest"))
      val firstPlaylists = first.playlists.first { it.isNotEmpty() }

      withApplication { secondGraph ->
        val second: PlaylistRepository = secondGraph.playlistRepository()
        assertThat(second).isNotSameInstanceAs(first)
        second.create(PlaylistName("Second guest"))

        val secondPlaylists = second.playlists.first { it.isNotEmpty() }
        assertThat(secondPlaylists.map { it.name.value }).isEqualTo(listOf("Second guest"))
        assertThat(first.playlists.value).isEqualTo(firstPlaylists)
      }
    }
  }

  @Test
  fun `guest playlists survive application recreation independently of application files`() =
    runTest {
      val factory: ScopedStorage.Factory = InMemoryStorageFactory()
      val episodeId = EpisodeId("episode")
      val (previousRepository, savedPlaylists) =
        withApplication(factory) { graph ->
          val repository: PlaylistRepository = graph.playlistRepository()
          repository.create(PlaylistName("Road trip"))
          val playlist = repository.playlists.first { it.isNotEmpty() }.single()
          repository.addEpisode(playlist.id, episodeId)
          val playlists = repository.playlists.first { it.single().episodeIds == listOf(episodeId) }

          graph.storage.files.deleteAll()
          repository to playlists
        }

      withApplication(factory) { graph ->
        val repository: PlaylistRepository = graph.playlistRepository()
        assertThat(repository).isNotSameInstanceAs(previousRepository)
        assertThat(repository.playlists.first { it.isNotEmpty() }).isEqualTo(savedPlaylists)
      }
    }

  private fun Graph.playlistRepository(): PlaylistRepository =
    userManager.userScope.value.metroDependencyGraph<UserBindings>().playlistRepository

  private suspend fun <T> withApplication(
    storageFactory: ScopedStorage.Factory = InMemoryStorageFactory(),
    block: suspend (Graph) -> T,
  ): T {
    val application = KmpApplication()
    val graph = createGraphFactory<Graph.Factory>().create(application, storageFactory)
    application.create(graph)
    try {
      return block(graph)
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
    val appRootPresenter: AppRootPresenter

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
    val playlistRepository: PlaylistRepository
  }
}
