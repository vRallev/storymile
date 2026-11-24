package software.ralf.storymile.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withCompositionLocal
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.presenterBackstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.playback.PlaybackPresenter
import software.ralf.storymile.presenternavigation.DefaultBackstackModel
import software.ralf.storymile.settings.SettingsPresenter
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
      val root = remember {
        ComposePresenter<Unit, AppTemplate.AdaptiveTemplate> { graph.presentRoot() }
      }
      presenterBackstack(root) { models ->
        val template = models.first() as AppTemplate.AdaptiveTemplate
        template.copy(
          content =
            DefaultBackstackModel(
              backstack = listOf(template.content) + models.drop(1),
              onBack = { pop() },
            ),
        )
      }
    }
  }

  @Composable
  private fun Graph.presentRoot(): AppTemplate.AdaptiveTemplate {
    val stack = LocalBackstackScope.requireNotNull()
    val openSettings = remember {
      {
        if (stack.backstack.size == 1) {
          stack.push(settingsPresenter)
        }
      }
    }
    return withCompositionLocal(LocalOpenSettings provides openSettings) {
      val tabs = remember { tabsPresenter }.present(Unit)
      val playback = remember { playbackPresenter }.present(Unit)
      AppTemplate.AdaptiveTemplate(
        content = tabs.content,
        tabs =
          tabs.copy(
            onSelectTab = { tab ->
              stack.pop()
              tabs.onSelectTab(tab)
            },
          ),
        playback = playback.collapsed,
        expandedPlayback = playback.expanded,
      )
    }
  }

  @ContributesTo(UserScope::class)
  interface Graph {
    val tabsPresenter: TabsPresenter

    val playbackPresenter: PlaybackPresenter

    val settingsPresenter: SettingsPresenter
  }
}
