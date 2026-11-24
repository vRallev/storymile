package software.ralf.storymile

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import software.ralf.storymile.user.IosGoogleLoginPlatform

/** Presents Google Sign-In from the active UIKit scene. */
interface IosGoogleSignIn {
  val configured: Boolean

  fun signIn(serverClientId: String, result: Result)

  fun signOut()

  /** Complete once when the SDK returns. */
  class Result
  internal constructor(
    private val continuation: CancellableContinuation<IosGoogleLoginPlatform.Tokens?>,
  ) {
    fun signedIn(idToken: String, accessToken: String, nonce: String) {
      continuation.resume(
        IosGoogleLoginPlatform.Tokens(idToken = idToken, accessToken = accessToken, nonce = nonce),
      )
    }

    fun cancelled() {
      continuation.resume(null)
    }

    fun failed() {
      continuation.resumeWithException(IllegalStateException("Google sign-in failed."))
    }
  }
}

internal fun IosGoogleSignIn.asHost() =
  IosGoogleLoginPlatform.Host(
    configured = configured,
    signIn = { clientId ->
      suspendCancellableCoroutine {
        signIn(serverClientId = clientId, result = IosGoogleSignIn.Result(it))
      }
    },
    signOut = { signOut() },
  )
