package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.user.DefaultGoogleLogin
import software.ralf.storymile.user.GoogleLogin
import software.ralf.storymile.user.User

/** Exercises account transitions without opening a browser or contacting an identity provider. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DefaultGoogleLogin::class])
class TestGoogleLogin : GoogleLogin {
  override val state: StateFlow<GoogleLogin.State>
    field = MutableStateFlow<GoogleLogin.State>(GoogleLogin.State.Guest)

  override fun signIn() {
    state.value = GoogleLogin.State.SignedIn(user = Listener, email = "listener@example.com")
  }

  override fun signOut() {
    state.value = GoogleLogin.State.Guest
  }

  private object Listener : User {
    override val id: String = "test-listener"
    override val isGuest: Boolean = false
  }
}
