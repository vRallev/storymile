package software.ralf.storymile.login

import kotlinx.coroutines.flow.StateFlow

/** Authenticates a listener for the current app session. */
interface Login {
  /** Account or sign-in status to display. */
  val state: StateFlow<State>

  /** Opens the sign-in flow. Dismissing it leaves the listener signed out. */
  fun signIn()

  /** Leaves the current account on this device. */
  fun signOut()

  /** State of the current sign-in session. */
  sealed interface State {
    /** Sign-in is unavailable in this build or environment. */
    data object Unavailable : State

    /** The listener can sign in. */
    data object SignedOut : State

    /** An account selection or verification is in progress. */
    data object SigningIn : State

    /** The listener can retry after an unsuccessful sign-in. */
    data object Failed : State

    /** The authenticated account. */
    data class SignedIn(
      /** Stable local identifier for this account's data, distinct from the guest identifier. */
      val id: String,
      /** Address shown in the account settings, if provided. */
      val email: String?,
    ) : State
  }
}
