package software.ralf.storymile.login

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.storymile.login.Login.State

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FirebaseLogin
internal constructor(
  private val google: GoogleSignIn,
  private val accounts: FirebaseAccounts,
  @ForScope(AppScope::class) private val scope: CoroutineScope,
  @MainCoroutineDispatcher private val main: CoroutineDispatcher,
) : Login {
  override val state: StateFlow<State>
    field =
      MutableStateFlow<State>(
        if (accounts.configured && google.available) State.SignedOut else State.Unavailable,
      )

  private val provider = Mutex()

  override fun signIn() {
    scope.launch(main) {
      if (state.value != State.SignedOut && state.value != State.Failed) {
        return@launch
      }
      state.value = State.SigningIn
      try {
        val token = provider.withLock { google.credential() }
        state.value = token?.let { accounts.signIn(it) } ?: State.SignedOut
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        state.value = State.Failed
      }
    }
  }

  override fun signOut() {
    scope.launch(main) {
      if (state.value !is State.SignedIn) {
        return@launch
      }
      state.value = State.SignedOut
      try {
        provider.withLock { google.signOut() }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // The local session has ended even if the provider cannot clear its account selection.
      }
    }
  }
}
