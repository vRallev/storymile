package software.ralf.storymile.approot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.playback.PlaybackPresenter
import software.ralf.storymile.playlist.PlaylistRepository
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

@Inject
class AppRootPresenter(
  private val tabsPresenter: TabsPresenter,
  private val playbackPresenter: PlaybackPresenter,
  private val userManager: UserManager,
) : ComposePresenter<Unit, AppTemplate> {
  @Composable
  override fun present(input: Unit): AppTemplate {
    val tabs = tabsPresenter.present(Unit)
    val userScope by userManager.userScope.collectAsState()
    val hasEpisodes =
      key(userScope) {
        val playlists by
          userScope
            .metroDependencyGraph<UserBindings>()
            .playlistRepository
            .playlists
            .collectAsState()
        playlists.any { it.episodeIds.isNotEmpty() }
      }
    val playback = playbackPresenter.present(Unit)
    return AppTemplate.AdaptiveTemplate(
      content = tabs.content,
      tabs = tabs,
      playback = playback.collapsed.takeIf { hasEpisodes },
      expandedPlayback = playback.expanded.takeIf { hasEpisodes },
    )
  }

  @ContributesTo(UserScope::class)
  interface UserBindings {
    val playlistRepository: PlaylistRepository
  }
}
