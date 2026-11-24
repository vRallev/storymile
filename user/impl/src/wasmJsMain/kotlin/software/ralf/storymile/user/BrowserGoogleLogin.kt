@file:OptIn(ExperimentalWasmJsInterop::class)

package software.ralf.storymile.user

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.url.URL
import org.w3c.dom.url.URLSearchParams

@ContributesBinding(AppScope::class)
class BrowserGoogleLogin
internal constructor(
  private val exchange: GoogleTokenExchange,
  private val embedded: Boolean,
  private val navigate: (url: String, pendingKey: String) -> Unit,
) : GoogleLoginPlatform {
  override val available: Boolean
    get() = GoogleLoginConfig.configured && supportsBrowserLogin()

  @Inject
  constructor(
    exchange: GoogleTokenExchange,
  ) : this(
    exchange = exchange,
    embedded = window !== window.top,
    navigate = { url, key -> navigateFromBrowser(url, key) },
  )

  override suspend fun signIn(): GoogleAccount? {
    val redirectUrl = window.location.origin + window.location.pathname
    if (embedded) {
      // Leave the same-origin documentation preview before opening the external provider.
      startNavigation(url = redirectUrl, key = standaloneKey, value = "requested")
      return null
    }
    val verifier = randomVerifier()
    val challenge = codeChallenge(verifier).await<JsString>().toString()
    val url = exchange.authorizeUrl(redirectUrl = redirectUrl, challenge = challenge)
    startNavigation(url = url, key = verifierKey, value = verifier)
    return null
  }

  override suspend fun resumeSignIn(): GoogleAccount? {
    if (embedded) {
      return null
    }
    if (sessionStorage.getItem(standaloneKey) != null) {
      sessionStorage.removeItem(standaloneKey)
      return signIn()
    }
    val url = URL(window.location.href)
    val fragment = URLSearchParams(url.hash.removePrefix("#").toJsString())
    val error = url.searchParams.get("error") ?: fragment.get("error")
    val code = url.searchParams.get("code")
    if (code == null && error == null) {
      sessionStorage.removeItem(verifierKey)
      return null
    }

    val verifier = sessionStorage.getItem(verifierKey)
    sessionStorage.removeItem(verifierKey)
    for (name in listOf("code", "error", "error_code", "error_description")) {
      url.searchParams.delete(name)
    }
    if (fragment.has("error")) {
      url.hash = ""
    }
    window.history.replaceState(data = null, title = "", url = url.href)
    if (error == "access_denied") {
      return null
    }
    check(error == null && !code.isNullOrBlank() && verifier != null) {
      "Google sign-in could not be completed. Start sign-in again."
    }
    return exchange.exchangeCode(code = code, verifier = verifier)
  }

  override suspend fun signOut() {
    sessionStorage.removeItem(standaloneKey)
    sessionStorage.removeItem(verifierKey)
  }

  private fun startNavigation(url: String, key: String, value: String) {
    sessionStorage.setItem(key, value)
    try {
      navigate(url, key)
    } catch (e: Exception) {
      sessionStorage.removeItem(key)
      throw e
    }
  }

  private companion object {
    const val verifierKey = "storymile.google-login.verifier"
    const val standaloneKey = "storymile.google-login.open-standalone"
  }
}

// A document restored by Back can keep its original Wasm instance without running startup again.
@JsFun(
  """(url, key) => {
    const pending = sessionStorage.getItem(key);
    window.addEventListener('pagehide', () => {
      window.addEventListener('pageshow', () => {
        if (sessionStorage.getItem(key) === pending) sessionStorage.removeItem(key);
      }, { once: true });
    }, { once: true });
    window.top.location.assign(url);
  }""",
)
private external fun navigateFromBrowser(url: String, key: String)

@JsFun(
  """() => {
    try { return window.isSecureContext && window.top.location.origin === window.location.origin; }
    catch { return false; }
  }""",
)
private external fun supportsBrowserLogin(): Boolean

@JsFun(
  """() => Array.from(crypto.getRandomValues(new Uint8Array(32)),
    byte => byte.toString(16).padStart(2, '0')).join('')""",
)
private external fun randomVerifier(): String

@JsFun(
  """async (verifier) => {
    const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
    return btoa(String.fromCharCode(...new Uint8Array(bytes)))
      .replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
  }""",
)
private external fun codeChallenge(verifier: String): Promise<JsString>
