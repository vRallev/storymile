package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.playlist.EpisodeId
import software.ralf.storymile.playlist.PlaylistRepository
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.InMemoryStorageFactory
import software.ralf.storymile.storage.ScopedStorage
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class PlaylistScopeTest {
  @Test
  fun `each guest scope owns one repository with separate playlists`() = runTest {
    withApplication { firstGraph ->
      val first: PlaylistRepository = firstGraph.playlistRepository()
      assertThat(firstGraph.playlistRepository()).isSameInstanceAs(first)
      first.create("First guest")
      val firstPlaylists = first.playlists.first { it.isNotEmpty() }

      withApplication { secondGraph ->
        val second: PlaylistRepository = secondGraph.playlistRepository()
        assertThat(second).isNotSameInstanceAs(first)
        second.create("Second guest")

        val secondPlaylists = second.playlists.first { it.isNotEmpty() }
        assertThat(secondPlaylists.map { it.name }).isEqualTo(listOf("Second guest"))
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
          repository.create("Road trip")
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
