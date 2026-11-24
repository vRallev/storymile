package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.login.Login

/** Lets integration tests switch accounts without contacting Google. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, priority = 1)
class TestLogin : Login {
  override val state: StateFlow<Login.State>
    field = MutableStateFlow<Login.State>(Login.State.SignedOut)

  override fun signIn() {
    state.value = Login.State.SignedIn(id = "test-listener", email = "listener@example.com")
  }

  override fun signOut() {
    state.value = Login.State.SignedOut
  }
}
