package software.ralf.storymile.approot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.playback.PlaybackPresenter
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.tabs.TabsPresenter
import software.ralf.storymile.templates.AppTemplate

@Inject
class AppRootPresenter(
  private val tabsPresenter: () -> TabsPresenter,
  private val playbackPresenter: () -> PlaybackPresenter,
  private val runtimeModeController: RuntimeModeController,
) : ComposePresenter<Unit, AppTemplate> {
  @Composable
  override fun present(input: Unit): AppTemplate {
    val mode by runtimeModeController.mode.collectAsState()
    return key(mode) {
      val tabs = remember { tabsPresenter() }.present(Unit)
      val playback = remember { playbackPresenter() }.present(Unit)
      AppTemplate.AdaptiveTemplate(
        content = tabs.content,
        tabs = tabs,
        playback = playback.collapsed,
        expandedPlayback = playback.expanded,
      )
    }
  }
}
