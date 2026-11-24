package software.ralf.storymile.user

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class GoogleTokenExchange
internal constructor(
  private val origin: String,
  private val publishableKey: String,
  private val createClient: () -> HttpClient,
) {
  @Inject
  constructor() :
    this(
      origin = GoogleLoginConfig.supabaseUrl,
      publishableKey = GoogleLoginConfig.publishableKey,
      createClient = {
        HttpClient {
          followRedirects = false
          install(HttpTimeout) { requestTimeoutMillis = 15_000 }
        }
      },
    )

  fun authorizeUrl(redirectUrl: String, challenge: String): String =
    URLBuilder("$origin/auth/v1/authorize")
      .apply {
        parameters.append("provider", "google")
        parameters.append("redirect_to", redirectUrl)
        parameters.append("code_challenge", challenge)
        parameters.append("code_challenge_method", "s256")
      }
      .buildString()

  suspend fun exchange(
    idToken: String,
    accessToken: String? = null,
    nonce: String? = null,
  ): GoogleAccount =
    token(
      grant = "id_token",
      body =
        buildJsonObject {
          put("provider", "google")
          put("id_token", idToken)
          accessToken?.let { put("access_token", it) }
          nonce?.let { put("nonce", it) }
        },
    )

  suspend fun exchangeCode(code: String, verifier: String): GoogleAccount =
    token(
      grant = "pkce",
      body =
        buildJsonObject {
          put("auth_code", code)
          put("code_verifier", verifier)
        },
    )

  private suspend fun token(grant: String, body: JsonObject): GoogleAccount =
    createClient().use { client ->
      val response =
        client.post("$origin/auth/v1/token?grant_type=$grant") {
          header("apikey", publishableKey)
          contentType(ContentType.Application.Json)
          setBody(body.toString())
        }
      check(response.status.isSuccess()) { "Google login was rejected." }
      val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject
      val user = result.getValue("user").jsonObject
      GoogleAccount(
        id = Uuid.parse(user.getValue("id").jsonPrimitive.content).toString(),
        email = user["email"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,
        accessToken = result.getValue("access_token").jsonPrimitive.content,
      )
    }

  suspend fun signOut(account: GoogleAccount) {
    try {
      createClient().use { client ->
        client.post("$origin/auth/v1/logout?scope=local") {
          header("apikey", publishableKey)
          bearerAuth(account.accessToken)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // An offline device still drops its only copy of the session credential.
    }
  }
}
