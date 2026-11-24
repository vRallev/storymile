package software.ralf.storymile.login

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsAny
import kotlinx.coroutines.suspendCancellableCoroutine

@ContributesBinding(AppScope::class)
class BrowserGoogleSignIn : GoogleSignIn {
  private val signIn by lazy { googleSignInDialog(LoginConfig.googleClientId) }

  override val available: Boolean
    get() = LoginConfig.googleClientId.isNotBlank() && supportsGoogleSignIn()

  override suspend fun credential(): String? = suspendCancellableCoroutine { continuation ->
    signIn.open(
      complete = { continuation.resume(it) },
      fail = {
        continuation.resumeWithException(IllegalStateException("Google sign-in is unavailable."))
      },
    )
    continuation.invokeOnCancellation { signIn.cancel() }
  }

  override suspend fun signOut() = signIn.disableAutoSelect()
}

private external interface GoogleSignInDialog : JsAny {
  fun open(complete: (String?) -> Unit, fail: () -> Unit)

  fun cancel()

  fun disableAutoSelect()
}

@JsFun(
  """() => {
  try { return window.isSecureContext && window.top.location.origin === location.origin; }
  catch { return false; }
}""",
)
private external fun supportsGoogleSignIn(): Boolean

@JsFun(
  """(clientId) => {
    let library;
    let finish;
    let state;
    function load() {
      if (!library) {
        library = new Promise((resolve, reject) => {
          if (window.google?.accounts?.id) { resolve(window.google.accounts.id); return; }
          const script = document.createElement('script');
          script.src = 'https://accounts.google.com/gsi/client';
          script.onload = () => resolve(window.google?.accounts?.id);
          script.onerror = () => { script.remove(); reject(new Error('Google sign-in unavailable')); };
          document.head.append(script);
        }).then(id => {
          id.initialize({client_id: clientId, callback: response => {
            if (response.state === state) finish?.(response.credential);
          }});
          return id;
        }).catch(error => { library = undefined; throw error; });
      }
      return library;
    }
    return {
      open(complete, fail) {
        state = crypto.randomUUID();
        const dialog = document.createElement('dialog');
        dialog.setAttribute('aria-label', 'Sign in to Storymile');
        dialog.style.cssText = 'border:0;border-radius:16px;padding:24px;color-scheme:light dark;font:16px system-ui';
        dialog.innerHTML = '<h2 style="margin:0 0 16px">Sign in to Storymile</h2><div></div><p><button type="button">Cancel</button></p>';
        finish = (token, failed) => {
          dialog.remove();
          finish = undefined;
          state = undefined;
          if (failed) fail(); else complete(token || null);
        };
        dialog.oncancel = event => { event.preventDefault(); finish?.(null); };
        dialog.querySelector('button').onclick = () => finish?.(null);
        document.body.append(dialog);
        dialog.showModal();
        load().then(id => {
          if (dialog.isConnected) id.renderButton(dialog.querySelector('div'), {type:'standard', state});
        }).catch(() => { if (dialog.isConnected) finish?.(null, true); });
      },
      cancel() { finish?.(null); },
      disableAutoSelect() { window.google?.accounts?.id?.disableAutoSelect(); }
    };
  }""",
)
private external fun googleSignInDialog(clientId: String): GoogleSignInDialog
