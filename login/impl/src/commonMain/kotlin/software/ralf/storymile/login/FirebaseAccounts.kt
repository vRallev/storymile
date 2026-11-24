package software.ralf.storymile.login

import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.ByteString.Companion.encodeUtf8

internal class FirebaseAccounts(
  private val apiKey: String,
  private val createClient: () -> HttpClient,
) {
  val configured: Boolean
    get() = apiKey.isNotBlank()

  @Inject
  constructor() :
    this(
      apiKey = LoginConfig.firebaseApiKey,
      createClient = {
        HttpClient {
          followRedirects = false
          install(HttpTimeout) { requestTimeoutMillis = 20_000 }
        }
      },
    )

  suspend fun signIn(googleIdToken: String): Login.State.SignedIn =
    createClient().use { client ->
      val credential = Parameters.build {
        append("id_token", googleIdToken)
        append("providerId", "google.com")
      }
      val response =
        client.post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp") {
          parameter("key", apiKey)
          contentType(ContentType.Application.Json)
          setBody(
            buildJsonObject {
              put("requestUri", "http://localhost")
              put("postBody", credential.formUrlEncode())
              put("returnSecureToken", true)
            }
              .toString(),
          )
        }
      check(response.status.isSuccess()) { "Firebase sign-in failed." }
      val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject
      // Firebase can return an account-linking challenge with a successful HTTP status.
      check(!result["idToken"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
        "Sign-in is incomplete."
      }
      val uid = result.getValue("localId").jsonPrimitive.contentOrNull
      require(!uid.isNullOrBlank())
      Login.State.SignedIn(
        id = uid.encodeUtf8().sha256().hex(),
        email = result["email"]?.jsonPrimitive?.contentOrNull,
      )
    }
}
