package software.ralf.storymile.user

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
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
import software.ralf.app.platform.scope.coroutine.IoCoroutineDispatcher

@ContributesBinding(AppScope::class)
class DesktopGoogleLogin(
  private val exchange: GoogleTokenExchange,
  @IoCoroutineDispatcher private val ioDispatcher: CoroutineDispatcher,
) : GoogleLoginPlatform {
  override val available: Boolean
    get() =
      GoogleLoginConfig.configured &&
        Desktop.isDesktopSupported() &&
        Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)

  override suspend fun signIn(): GoogleAccount? =
    withContext(ioDispatcher) {
      val random = SecureRandom()
      val encoder = Base64.getUrlEncoder().withoutPadding()
      val verifier = encoder.encodeToString(ByteArray(32).also { random.nextBytes(it) })
      val challenge =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
      val path =
        "/google-login/" + encoder.encodeToString(ByteArray(16).also { random.nextBytes(it) })
      val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      val callback = CompletableDeferred<String?>()
      val code =
        try {
          server.createContext(path) { request ->
            handleCallback(request = request, path = path, result = callback)
          }
          server.start()
          val redirect = "http://127.0.0.1:${server.address.port}$path"
          val url = exchange.authorizeUrl(redirectUrl = redirect, challenge = challenge)
          Desktop.getDesktop().browse(URI(url))
          withTimeoutOrNull(3.minutes) { callback.await() }
        } finally {
          server.stop(0)
        }
      code?.let { exchange.exchangeCode(code = it, verifier = verifier) }
    }

  override suspend fun signOut() = Unit

  private fun handleCallback(
    request: HttpExchange,
    path: String,
    result: CompletableDeferred<String?>,
  ) {
    request.use {
      if (request.requestMethod != "GET" || request.requestURI.rawPath != path) {
        request.sendResponseHeaders(404, -1)
        return
      }
      val query = parseQueryString(request.requestURI.rawQuery.orEmpty())
      val error = query["error"]
      val code = query["code"]?.takeIf { it.isNotBlank() && error == null }
      val response =
        if (code == null) {
          "Return to Storymile to try again."
        } else {
          "Return to Storymile to finish signing in."
        }
      request.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
      request.responseHeaders.set("Cache-Control", "no-store")
      val bytes = response.toByteArray()
      request.sendResponseHeaders(200, bytes.size.toLong())
      try {
        request.responseBody.use { it.write(bytes) }
      } finally {
        if (error != null && error != "access_denied") {
          result.completeExceptionally(IllegalStateException("Google sign-in failed."))
        } else {
          result.complete(code)
        }
      }
    }
  }
}
