package software.ralf.storymile.login

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Inject
@ContributesBinding(AppScope::class)
class IosGoogleSignIn(private val host: IosGoogleSignInHost) : GoogleSignIn {
  override val available: Boolean
    get() = host.configured && LoginConfig.googleClientId.isNotBlank()

  override suspend fun credential(): String? =
    withContext(Dispatchers.Main.immediate) {
      suspendCancellableCoroutine { continuation ->
        host.signIn(
          serverClientId = LoginConfig.googleClientId,
          completion = { continuation.resume(it) },
          failed = {
            continuation.resumeWithException(IllegalStateException("Google sign-in failed."))
          },
        )
      }
    }

  override suspend fun signOut() {
    withContext(Dispatchers.Main.immediate) { host.signOut() }
  }
}
