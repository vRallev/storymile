package software.ralf.storymile.approot

import androidx.compose.runtime.Composable
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.playback.PlaybackPresenter
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

@Inject
class AppRootPresenter(
  private val tabsPresenter: TabsPresenter,
  private val playbackPresenter: PlaybackPresenter,
) : ComposePresenter<Unit, AppTemplate> {
  @Composable
  override fun present(input: Unit): AppTemplate {
    val tabs = tabsPresenter.present(Unit)
    val playback = playbackPresenter.present(Unit)
    return AppTemplate.AdaptiveTemplate(
      content = tabs.content,
      tabs = tabs,
      playback = playback.collapsed,
      expandedPlayback = playback.expanded,
    )
  }
}
