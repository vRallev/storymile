package software.ralf.storymile.login

import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding

@ContributesBinding(AppScope::class)
class AndroidGoogleSignIn(
  application: Application,
  private val host: AndroidLoginHost,
) : GoogleSignIn {
  override val available: Boolean
    get() = LoginConfig.googleClientId.isNotBlank()

  private val credentials by lazy { CredentialManager.create(application) }

  override suspend fun credential(): String? {
    val option = GetSignInWithGoogleOption.Builder(LoginConfig.googleClientId).build()
    val result =
      try {
        credentials.getCredential(
          context = checkNotNull(host.activity),
          request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
      } catch (_: GetCredentialCancellationException) {
        return null
      }
    check(result.credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
    return GoogleIdTokenCredential.createFrom(result.credential.data).idToken
  }

  override suspend fun signOut() {
    credentials.clearCredentialState(ClearCredentialStateRequest())
  }
}
