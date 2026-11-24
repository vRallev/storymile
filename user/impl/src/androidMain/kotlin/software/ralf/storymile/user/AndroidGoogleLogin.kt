package software.ralf.storymile.user

import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import java.security.MessageDigest
import java.security.SecureRandom

@ContributesBinding(AppScope::class)
class AndroidGoogleLogin(
  application: Application,
  private val host: AndroidGoogleLoginHost,
  private val exchange: GoogleTokenExchange,
) : GoogleLoginPlatform {
  override val available: Boolean
    get() = GoogleLoginConfig.configured && GoogleLoginConfig.googleWebClientId.isNotBlank()

  private val credentials by lazy { CredentialManager.create(application) }

  override suspend fun signIn(): GoogleAccount? {
    val activity = checkNotNull(host.activity)
    val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }.toHexString()
    val challenge = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()).toHexString()
    val option =
      GetSignInWithGoogleOption.Builder(GoogleLoginConfig.googleWebClientId)
        .setNonce(challenge)
        .build()
    val result =
      try {
        credentials.getCredential(
          context = activity,
          request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
      } catch (_: GetCredentialCancellationException) {
        return null
      }
    check(result.credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
    val token = GoogleIdTokenCredential.createFrom(result.credential.data)
    return exchange.exchange(idToken = token.idToken, nonce = nonce)
  }

  override suspend fun signOut() {
    credentials.clearCredentialState(ClearCredentialStateRequest())
  }
}
