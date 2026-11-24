package software.ralf.storymile.user

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import software.ralf.app.platform.scope.runTestWithScope

class DefaultGoogleLoginTest {
  @Test
  fun `dismissal and failure allow retry while repeated clicks open one chooser`() =
    runTestWithScope { scope ->
      val platform = TestPlatform()
      val auth =
        DefaultGoogleLogin(
          platform = platform,
          exchange =
            GoogleTokenExchange(
              origin = "https://project.example",
              publishableKey = "public-key",
              createClient = { HttpClient(MockEngine { respond("") }) },
            ),
          scope = backgroundScope,
          mainDispatcher = StandardTestDispatcher(testScheduler),
        )
      scope.register(auth)
      val login: GoogleLogin = auth
      runCurrent()
      assertThat(login.state.value).isEqualTo(GoogleLogin.State.Guest)

      login.signIn()
      login.signIn()
      runCurrent()
      assertThat(platform.attempts).isEqualTo(1)
      assertThat(login.state.value).isEqualTo(GoogleLogin.State.SigningIn)
      platform.result.complete(null)
      runCurrent()
      assertThat(login.state.value).isEqualTo(GoogleLogin.State.Guest)

      platform.result = CompletableDeferred()
      login.signIn()
      platform.result.completeExceptionally(IllegalStateException("provider unavailable"))
      runCurrent()
      assertThat(login.state.value).isEqualTo(GoogleLogin.State.Failed)

      platform.result = CompletableDeferred()
      val account = GoogleAccount(id = "verified", email = null, accessToken = "test")
      login.signIn()
      platform.result.complete(account)
      runCurrent()
      assertThat(login.state.value)
        .isEqualTo(GoogleLogin.State.SignedIn(user = account, email = null))
      login.signOut()
      runCurrent()
      assertThat(login.state.value).isEqualTo(GoogleLogin.State.Guest)
      assertThat(platform.signedOut).isEqualTo(true)
    }

  private class TestPlatform : GoogleLoginPlatform {
    override val available = true
    var attempts = 0
    var signedOut = false
    var result = CompletableDeferred<GoogleAccount?>()

    override suspend fun signIn(): GoogleAccount? {
      attempts++
      return result.await()
    }

    override suspend fun signOut() {
      signedOut = true
    }
  }
}
