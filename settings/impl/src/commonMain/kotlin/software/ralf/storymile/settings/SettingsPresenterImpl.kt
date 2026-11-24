package software.ralf.storymile.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.storymile.theme.ThemeController
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class SettingsPresenterImpl(
  private val themeController: ThemeController,
  private val userManager: UserManager,
) : SettingsPresenter {
  @Composable
  override fun present(input: Unit): SettingsPresenter.Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val coroutineScope = rememberCoroutineScope()
    val themeMode by themeController.mode.collectAsState()
    val user by userManager.user.collectAsState()
    var selectedSection by remember { mutableStateOf(SettingsPresenter.Section.PLAYBACK) }
    var confirmSignOut by remember(user.isGuest) { mutableStateOf(false) }
    var accountActionInProgress by remember { mutableStateOf(false) }
    var accountActionFailed by remember { mutableStateOf(false) }

    fun runAccountAction(action: suspend () -> Unit) {
      if (accountActionInProgress) {
        return
      }
      confirmSignOut = false
      accountActionInProgress = true
      accountActionFailed = false
      coroutineScope.launch {
        try {
          action()
        } catch (exception: CancellationException) {
          throw exception
        } catch (_: Exception) {
          accountActionFailed = true
        } finally {
          accountActionInProgress = false
        }
      }
    }

    return SettingsPresenter.Model(
      account =
        if (user.isGuest) SettingsPresenter.Account.Guest else SettingsPresenter.Account.Fake,
      selectedSection = selectedSection,
      themeMode = themeMode,
      onSelectSection = { selectedSection = it },
      onSelectThemeMode = { themeController.setMode(it) },
      onBack = { backstack.pop() },
      onAccountLongPress =
        if (accountActionInProgress) {
          null
        } else if (user.isGuest) {
          { runAccountAction { userManager.signIn() } }
        } else {
          { confirmSignOut = true }
        },
      signOutConfirmation =
        if (confirmSignOut) {
          SettingsPresenter.SignOutConfirmation(
            onKeepFiles = { runAccountAction { userManager.signOut(keepData = true) } },
            onWipeFiles = { runAccountAction { userManager.signOut(keepData = false) } },
            onCancel = { confirmSignOut = false },
          )
        } else {
          null
        },
      accountActionFailed = accountActionFailed,
    )
  }
}
