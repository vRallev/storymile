package software.ralf.storymile.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.storymile.theme.ThemeController
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class SettingsPresenterImpl(private val themeController: ThemeController) : SettingsPresenter {
  @Composable
  override fun present(input: Unit): SettingsPresenter.Model {
    val backstack = LocalBackstackScope.requireNotNull()
    var selectedSection by remember { mutableStateOf(SettingsPresenter.Section.PLAYBACK) }
    val themeMode by themeController.mode.collectAsState()
    return SettingsPresenter.Model(
      account = SettingsPresenter.Account.Guest,
      selectedSection = selectedSection,
      themeMode = themeMode,
      onSelectSection = { selectedSection = it },
      onSelectThemeMode = { themeController.setMode(it) },
      onBack = { backstack.pop() },
    )
  }
}
