package software.ralf.storymile.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.playback.PlaybackPresenter
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

@Inject
@ContributesBinding(AppScope::class, binding = binding<UserPresenter<*>>())
class UserPresenterImpl(private val userManager: UserManager) : UserPresenter<AppTemplate> {
  @Composable
  override fun present(input: Unit): AppTemplate {
    val userScope by userManager.userScope.collectAsState()
    return key(userScope) {
      val graph = remember { userScope.metroDependencyGraph<Graph>() }
      val tabsPresenter = remember { graph.tabsPresenter }
      val playbackPresenter = remember { graph.playbackPresenter }
      val tabs = tabsPresenter.present(Unit)
      val playback = playbackPresenter.present(Unit)
      AppTemplate.AdaptiveTemplate(
        content = tabs.content,
        tabs = tabs,
        playback = playback.collapsed,
        expandedPlayback = playback.expanded,
      )
    }
  }

  @ContributesTo(UserScope::class)
  interface Graph {
    val tabsPresenter: TabsPresenter

    val playbackPresenter: PlaybackPresenter
  }
}
