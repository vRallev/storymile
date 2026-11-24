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
import software.ralf.storymile.login.Login
import software.ralf.storymile.theme.ThemeController
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class SettingsPresenterImpl(
  private val themeController: ThemeController,
  private val login: Login,
) : SettingsPresenter {
  @Composable
  override fun present(input: Unit): SettingsPresenter.Model {
    val backstack = LocalBackstackScope.requireNotNull()
    var selectedSection by remember { mutableStateOf(SettingsPresenter.Section.PLAYBACK) }
    val themeMode by themeController.mode.collectAsState()
    val account by login.state.collectAsState()
    return SettingsPresenter.Model(
      account = account,
      onAccountAction =
        if (account is Login.State.SignedIn) {
          { login.signOut() }
        } else {
          { login.signIn() }
        },
      selectedSection = selectedSection,
      themeMode = themeMode,
      onSelectSection = { selectedSection = it },
      onSelectThemeMode = { themeController.setMode(it) },
      onBack = { backstack.pop() },
    )
  }
}
