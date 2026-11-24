package software.ralf.storymile.user

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.storymile.user.GoogleLogin.State

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class DefaultGoogleLogin(
  private val platform: GoogleLoginPlatform,
  private val exchange: GoogleTokenExchange,
  @ForScope(AppScope::class) private val scope: CoroutineScope,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
) : GoogleLogin, Scoped {
  override val state: StateFlow<State>
    field = MutableStateFlow<State>(if (platform.available) State.SigningIn else State.Unavailable)

  private var account: GoogleAccount? = null

  override fun onEnterScope(scope: Scope) {
    if (platform.available) {
      this.scope.launch(mainDispatcher) { authenticate { platform.resumeSignIn() } }
    }
  }

  override fun signIn() {
    scope.launch(mainDispatcher) {
      if (state.value == State.Guest || state.value == State.Failed) {
        state.value = State.SigningIn
        authenticate { platform.signIn() }
      }
    }
  }

  override fun signOut() {
    scope.launch(mainDispatcher) {
      val previous = account ?: return@launch
      account = null
      state.value = State.SigningOut
      try {
        platform.signOut()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // The local account is signed out even if the credential provider is unavailable.
      } finally {
        state.value = State.Guest
      }
      exchange.signOut(previous)
    }
  }

  private suspend fun authenticate(request: suspend () -> GoogleAccount?) {
    try {
      val verified = request()
      account = verified
      state.value = verified?.let { State.SignedIn(user = it, email = it.email) } ?: State.Guest
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      state.value = State.Failed
    }
  }
}
