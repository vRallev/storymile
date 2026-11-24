package software.ralf.storymile.user

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GoogleTokenExchangeTest {
  @Test
  fun `native credentials use the broker identity and sign out only this session`() = runTest {
    val id = "f179d506-052b-45c4-8b66-f03ff680ee8f"
    var signedOut = false
    val exchange = exchange { request ->
      assertThat(request.headers["apikey"]).isEqualTo("public-key")
      when (request.url.encodedPath) {
        "/auth/v1/token" -> {
          assertThat(request.url.parameters["grant_type"]).isEqualTo("id_token")
          val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
          assertThat(body.getValue("provider").jsonPrimitive.content).isEqualTo("google")
          assertThat(body.getValue("nonce").jsonPrimitive.content).isEqualTo("original-nonce")
          assertThat(body.getValue("id_token").jsonPrimitive.content).isEqualTo("provider-token")
          respond(
            """{"access_token":"session","user":{"id":"$id","email":"listener@example.test"}}""",
          )
        }
        "/auth/v1/logout" -> {
          assertThat(request.headers[HttpHeaders.Authorization]).isEqualTo("Bearer session")
          assertThat(request.url.parameters["scope"]).isEqualTo("local")
          signedOut = true
          respond("")
        }
        else -> error("Unexpected request")
      }
    }

    val account = exchange.exchange(idToken = "provider-token", nonce = "original-nonce")
    assertThat(account.id).isEqualTo(id)
    assertThat(account.email).isEqualTo("listener@example.test")
    exchange.signOut(account)
    assertThat(signedOut).isEqualTo(true)
  }

  @Test
  fun `PKCE uses the allowlisted callback and redeems the verifier only at the broker`() = runTest {
    val exchange = exchange { request ->
      assertThat(request.url.host).isEqualTo("project.example")
      assertThat(request.url.parameters["grant_type"]).isEqualTo("pkce")
      val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
      assertThat(body.getValue("auth_code").jsonPrimitive.content).isEqualTo("one-use-code")
      assertThat(body.getValue("code_verifier").jsonPrimitive.content).isEqualTo("private-verifier")
      respond("invalid code", HttpStatusCode.BadRequest)
    }
    val callback = "http://127.0.0.1:43331/google-login/attempt"
    val url = Url(exchange.authorizeUrl(redirectUrl = callback, challenge = "challenge"))
    assertThat(url.parameters["redirect_to"]).isEqualTo(callback)
    assertThat(url.parameters["code_challenge"]).isEqualTo("challenge")
    assertThat(url.parameters["code_challenge_method"]).isEqualTo("s256")
    assertFailure { exchange.exchangeCode(code = "one-use-code", verifier = "private-verifier") }
      .isInstanceOf<IllegalStateException>()
  }

  @Test
  fun `a malformed broker identity cannot become a local storage path`() = runTest {
    val exchange = exchange { respond("""{"user":{"id":"../guest"},"access_token":"session"}""") }
    assertFailure { exchange.exchange("provider-token") }.isInstanceOf<IllegalArgumentException>()
  }

  private fun exchange(handler: MockRequestHandler) =
    GoogleTokenExchange(
      origin = "https://project.example",
      publishableKey = "public-key",
      createClient = { HttpClient(MockEngine(handler)) },
    )
}
