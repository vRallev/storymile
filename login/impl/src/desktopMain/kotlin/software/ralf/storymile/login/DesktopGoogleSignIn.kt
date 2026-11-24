package software.ralf.storymile.login

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parseQueryString
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import software.ralf.app.platform.scope.coroutine.IoCoroutineDispatcher

@ContributesBinding(AppScope::class)
class DesktopGoogleSignIn(
  @IoCoroutineDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GoogleSignIn {
  private val random by lazy { SecureRandom.getInstanceStrong() }
  private val installed: JsonObject? by lazy {
    try {
      javaClass.getResourceAsStream("/google-desktop-client.json")?.bufferedReader()?.use {
        val client = Json.parseToJsonElement(it.readText()).jsonObject
        client["installed"]?.jsonObject?.takeIf { installed ->
          "web" !in client &&
            !installed["client_id"]?.jsonPrimitive?.content.isNullOrBlank() &&
            !installed["client_secret"]?.jsonPrimitive?.content.isNullOrBlank()
        }
      }
    } catch (_: Exception) {
      // Configuration parsing errors may include parts of the client file.
      null
    }
  }

  override val available: Boolean
    get() =
      Desktop.isDesktopSupported() &&
        Desktop.getDesktop().isSupported(Desktop.Action.BROWSE) &&
        installed != null

  override suspend fun credential(): String? =
    withContext(ioDispatcher) {
      val client = checkNotNull(installed)
      val clientId = client.getValue("client_id").jsonPrimitive.content
      val verifier = randomValue()
      val state = randomValue()
      val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      val redirect = "http://127.0.0.1:${server.address.port}/"
      val result = CompletableDeferred<String?>()
      val code =
        try {
          server.createContext("/") { request ->
            receiveCode(request = request, state = state, result = result)
          }
          server.start()
          val url =
            authorizationUrl(
              clientId = clientId,
              redirect = redirect,
              state = state,
              verifier = verifier,
            )
          Desktop.getDesktop().browse(URI(url))
          withTimeoutOrNull(3.minutes) { result.await() }
        } finally {
          server.stop(0)
        } ?: return@withContext null
      HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 20_000 }
      }
        .use { http ->
          val response =
            http.submitForm(
              url = "https://oauth2.googleapis.com/token",
              formParameters =
                Parameters.build {
                  append("grant_type", "authorization_code")
                  append("client_id", clientId)
                  append("client_secret", client.getValue("client_secret").jsonPrimitive.content)
                  append("redirect_uri", redirect)
                  append("code_verifier", verifier)
                  append("code", code)
                },
            )
          check(response.status.isSuccess()) { "Google rejected the desktop sign-in." }
          Json.parseToJsonElement(response.bodyAsText())
            .jsonObject
            .getValue("id_token")
            .jsonPrimitive
            .content
        }
    }

  private fun authorizationUrl(
    clientId: String,
    redirect: String,
    state: String,
    verifier: String,
  ): String =
    URLBuilder("https://accounts.google.com/o/oauth2/v2/auth")
      .apply {
        parameters.append("client_id", clientId)
        parameters.append("redirect_uri", redirect)
        parameters.append("response_type", "code")
        parameters.append("scope", "openid email")
        parameters.append("state", state)
        parameters.append("code_challenge_method", "S256")
        parameters.append(
          "code_challenge",
          base64(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())),
        )
      }
      .buildString()

  private fun receiveCode(
    request: HttpExchange,
    state: String,
    result: CompletableDeferred<String?>,
  ) {
    request.use {
      val query = parseQueryString(request.requestURI.rawQuery.orEmpty())
      if (
        request.requestMethod != "GET" ||
          request.requestURI.rawPath != "/" ||
          query["state"] != state
      ) {
        request.sendResponseHeaders(404, -1)
        return
      }
      val message = "You can return to Storymile.".encodeToByteArray()
      request.responseHeaders.set("Cache-Control", "no-store")
      request.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
      try {
        request.sendResponseHeaders(200, message.size.toLong())
        request.responseBody.use { it.write(message) }
      } finally {
        when (query["error"]) {
          null -> result.complete(query["code"]?.takeIf { it.isNotBlank() })
          "access_denied" -> result.complete(null)
          else -> result.completeExceptionally(IllegalStateException("Google sign-in failed."))
        }
      }
    }
  }

  private fun randomValue(): String = base64(ByteArray(32).also(random::nextBytes))

  private fun base64(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
