package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.playlist.PlaylistName
import software.ralf.storymile.playlist.PlaylistRepository
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

/** Sets up an empty library without changing the test runtime mode. */
object TestPlaylists {
  /** Waits for the current user's repository to load, then removes all playlists. */
  suspend fun clear(rootScope: Scope) {
    val userScope = rootScope.metroDependencyGraph<AppBindings>().userManager.userScope.value
    clear(userScope.metroDependencyGraph<UserBindings>().playlistRepository)
  }

  /** Waits for [repository] to load, then removes all playlists. */
  suspend fun clear(repository: PlaylistRepository) {
    val marker = PlaylistName("Test setup ${Uuid.random()}")
    repository.create(marker)
    val loaded = repository.playlists.first { playlists -> playlists.any { it.name == marker } }
    loaded.forEach { repository.delete(it.id) }
    repository.playlists.first { it.isEmpty() }
  }

  /** Finds the active user for test setup. */
  @ContributesTo(AppScope::class)
  interface AppBindings {
    /** Active user scope owner. */
    val userManager: UserManager
  }

  /** Finds the current user's playlists for test setup. */
  @ContributesTo(UserScope::class)
  interface UserBindings {
    /** User-scoped playlist repository. */
    val playlistRepository: PlaylistRepository
  }
}
