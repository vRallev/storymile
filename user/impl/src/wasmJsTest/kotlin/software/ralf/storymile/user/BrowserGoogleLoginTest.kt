@file:OptIn(ExperimentalWasmJsInterop::class)

package software.ralf.storymile.user

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.url.URL

class BrowserGoogleLoginTest {
  @Test
  fun returningFromGoogleConsumesTheCodeOnceAndPreservesThePage() = runTest {
    val originalUrl = window.location.href
    val accountId = "11ed4451-b12f-403b-afae-0a203fff20a2"
    var exchanges = 0
    val exchange =
      GoogleTokenExchange(
        origin = "https://example.invalid",
        publishableKey = "test-key",
        createClient = {
          HttpClient(
            MockEngine { request ->
              val body =
                Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
              assertThat(body.getValue("auth_code").jsonPrimitive.content)
                .isEqualTo("returned-code")
              assertThat(body.getValue("code_verifier").jsonPrimitive.content.length).isEqualTo(64)
              exchanges++
              respond("""{"user":{"id":"$accountId"},"access_token":"test-token"}""")
            },
          )
        },
      )
    var destination = ""
    val preview: GoogleLoginPlatform =
      BrowserGoogleLogin(
        exchange = exchange,
        embedded = true,
        navigate = { url, _ -> destination = url },
      )
    val login: GoogleLoginPlatform =
      BrowserGoogleLogin(
        exchange = exchange,
        embedded = false,
        navigate = { url, _ -> destination = url },
      )
    try {
      val returnUrl = window.location.origin + window.location.pathname
      preview.signIn()
      assertThat(destination).isEqualTo(returnUrl)
      login.resumeSignIn()
      val brokerUrl = URL(destination)
      assertThat(brokerUrl.searchParams.get("redirect_to")).isEqualTo(returnUrl)
      assertThat(brokerUrl.searchParams.get("code_challenge")?.length).isEqualTo(43)

      val callback = "$returnUrl?keep=yes&code=returned-code#section"
      window.history.replaceState(data = null, title = "", url = callback)
      val restartedLogin: GoogleLoginPlatform =
        BrowserGoogleLogin(
          exchange = exchange,
          embedded = false,
          navigate = { _, _ -> error("Unexpected navigation") },
        )
      assertThat(restartedLogin.resumeSignIn()?.id).isEqualTo(accountId)
      assertThat(window.location.href).isEqualTo("$returnUrl?keep=yes#section")
      assertThat(restartedLogin.resumeSignIn()).isNull()
      window.history.replaceState(data = null, title = "", url = callback)
      assertFailure { restartedLogin.resumeSignIn() }.isInstanceOf<IllegalStateException>()
      assertThat(exchanges).isEqualTo(1)
    } finally {
      login.signOut()
      window.history.replaceState(data = null, title = "", url = originalUrl)
    }
  }
}
