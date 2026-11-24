package software.ralf.storymile.user

import kotlinx.coroutines.flow.StateFlow

/** Signs the current listener in with Google for this app session. */
interface GoogleLogin {
  /** Sign-in availability, progress, or the account verified by the authentication service. */
  val state: StateFlow<State>

  /** Starts a sign-in attempt or retries a failed one. Dismissing the chooser keeps the guest. */
  fun signIn()

  /** Leaves the account and returns to the local guest library. */
  fun signOut()

  /** Current state of the Google login action. */
  sealed interface State {
    /** This build lacks the public provider configuration. */
    data object Unavailable : State

    /** The listener can start sign-in. */
    data object Guest : State

    /** A provider request or redirect is in progress. */
    data object SigningIn : State

    /** The account has been detached while the provider clears its local state. */
    data object SigningOut : State

    /** The previous request failed. The guest can retry. */
    data object Failed : State

    /** The authentication service verified the listener. */
    data class SignedIn(
      /** Owner of the account's local library. */
      val user: User,
      /** Address to display in Settings, when supplied by Google. */
      val email: String?,
    ) : State
  }
}
