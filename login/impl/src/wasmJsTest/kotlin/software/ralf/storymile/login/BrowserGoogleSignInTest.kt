package software.ralf.storymile.login

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.test.Test
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest

class BrowserGoogleSignInTest {
  @Test
  fun aDismissedGoogleButtonCannotCompleteTheNextAttempt() = runTest {
    val provider = fakeGoogleProvider()
    val signIn: GoogleSignIn = BrowserGoogleSignIn()
    try {
      val firstButton = provider.nextButton()
      val dismissed = async(start = CoroutineStart.UNDISPATCHED) { signIn.credential() }
      val firstState = firstButton.await<JsString>().toString()
      provider.dismiss()
      assertThat(dismissed.await()).isNull()

      val secondButton = provider.nextButton()
      val current = async(start = CoroutineStart.UNDISPATCHED) { signIn.credential() }
      val currentState = secondButton.await<JsString>().toString()
      provider.respond(state = firstState, credential = "previous")
      assertThat(current.isCompleted).isFalse()
      provider.respond(state = currentState, credential = "current")
      assertThat(current.await()).isEqualTo("current")
    } finally {
      provider.restore()
    }
  }
}

private external interface FakeGoogleProvider : JsAny {
  fun nextButton(): Promise<JsString>

  fun respond(state: String, credential: String)

  fun dismiss()

  fun restore()
}

@JsFun(
  """() => {
    const original = window.google;
    let callback, rendered;
    window.google = { accounts: { id: {
      initialize: options => { callback = options.callback; },
      renderButton: (parent, options) => rendered(options.state),
    } } };
    return {
      nextButton: () => new Promise(resolve => { rendered = resolve; }),
      respond: (state, credential) => callback({ state, credential }),
      dismiss: () => document.querySelector('dialog[open] button').click(),
      restore: () => { window.google = original; },
    };
  }""",
)
private external fun fakeGoogleProvider(): FakeGoogleProvider
