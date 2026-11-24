package software.ralf.storymile.login

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.parseQueryString
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FirebaseLoginTest {
  @Test
  fun `only a completed Firebase exchange can open the account`() = runTest {
    val chooser = TestGoogleSignIn()
    var response = """{"needConfirmation":true,"email":"listener@example.test"}"""
    var exchanges = 0
    val accounts =
      FirebaseAccounts(
        apiKey = "test-key",
        createClient = {
          HttpClient(
            MockEngine { request ->
              assertThat(request.url.host).isEqualTo("identitytoolkit.googleapis.com")
              assertThat(request.url.encodedPath).isEqualTo("/v1/accounts:signInWithIdp")
              assertThat(request.url.parameters["key"]).isEqualTo("test-key")
              val body =
                Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
              val credential = parseQueryString(body.getValue("postBody").jsonPrimitive.content)
              assertThat(credential["providerId"]).isEqualTo("google.com")
              assertThat(credential["id_token"]).isEqualTo("google-token")
              exchanges++
              respond(response)
            },
          )
        },
      )
    val login: Login =
      FirebaseLogin(
        google = chooser,
        accounts = accounts,
        scope = this,
        main = StandardTestDispatcher(testScheduler),
      )
    login.signIn()
    login.signIn()
    runCurrent()
    assertThat(chooser.attempts).isEqualTo(1)
    assertThat(login.state.value).isEqualTo(Login.State.SigningIn)
    chooser.token.complete(null)
    runCurrent()
    assertThat(login.state.value).isEqualTo(Login.State.SignedOut)
    assertThat(exchanges).isEqualTo(0)

    chooser.token = CompletableDeferred("google-token")
    login.signIn()
    login.state.first { it == Login.State.Failed }

    response = """{"localId":"guest","idToken":"firebase-token","email":"listener@example.test"}"""
    login.signIn()
    val account = login.state.filterIsInstance<Login.State.SignedIn>().first()
    assertThat(account.email).isEqualTo("listener@example.test")
    assertThat(account.id.matches(Regex("[0-9a-f]{64}"))).isTrue()
    login.signOut()
    runCurrent()
    assertThat(login.state.value).isEqualTo(Login.State.SignedOut)
    assertThat(chooser.signedOut).isTrue()
  }

  private class TestGoogleSignIn : GoogleSignIn {
    override val available = true
    var token = CompletableDeferred<String?>()
    var attempts = 0
    var signedOut = false

    override suspend fun credential(): String? {
      attempts++
      return token.await()
    }

    override suspend fun signOut() {
      signedOut = true
    }
  }
}
