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
import software.ralf.storymile.user.GoogleLogin
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class SettingsPresenterImpl(private val login: GoogleLogin) : SettingsPresenter {
  @Composable
  override fun present(input: Unit): SettingsPresenter.Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val account by login.state.collectAsState()
    var selectedSection by remember { mutableStateOf(SettingsPresenter.Section.PLAYBACK) }
    return SettingsPresenter.Model(
      account = account,
      onSignIn = { login.signIn() },
      onSignOut = { login.signOut() },
      selectedSection = selectedSection,
      onSelectSection = { selectedSection = it },
      onBack = { backstack.pop() },
    )
  }
}
