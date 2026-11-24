package software.ralf.storymile.user

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Inject
@ContributesBinding(AppScope::class)
class IosGoogleLoginPlatform(private val host: Host, private val exchange: GoogleTokenExchange) :
  GoogleLoginPlatform {
  override val available: Boolean
    get() =
      host.configured &&
        GoogleLoginConfig.configured &&
        GoogleLoginConfig.googleWebClientId.isNotBlank()

  override suspend fun signIn(): GoogleAccount? {
    check(available) { "Google sign-in is not configured." }
    val tokens =
      withContext(Dispatchers.Main.immediate) { host.signIn(GoogleLoginConfig.googleWebClientId) }
        ?: return null
    return exchange.exchange(
      idToken = tokens.idToken,
      accessToken = tokens.accessToken,
      nonce = tokens.nonce,
    )
  }

  override suspend fun signOut() {
    withContext(Dispatchers.Main.immediate) { host.signOut() }
  }

  class Host(
    val configured: Boolean,
    val signIn: suspend (String) -> Tokens?,
    val signOut: () -> Unit,
  )

  class Tokens(val idToken: String, val accessToken: String, val nonce: String)
}
