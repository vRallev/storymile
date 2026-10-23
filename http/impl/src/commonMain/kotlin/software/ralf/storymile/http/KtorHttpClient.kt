package software.ralf.storymile.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scoped

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class KtorHttpClient(private val factory: KtorClientFactory) : HttpClient, Scoped {
  private val lifetime = Job()
  private val client by lazy {
    factory
      .create {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
          requestTimeoutMillis = 30_000
          connectTimeoutMillis = 10_000
          socketTimeoutMillis = 15_000
        }
      }
      .also { client ->
        lifetime.invokeOnCompletion {
          client.coroutineContext.cancel()
          client.close()
        }
      }
  }

  override fun onExitScope() {
    lifetime.cancel()
  }

  override suspend fun <T : Any> get(
    url: String,
    parameters: Map<String, String>,
    decode: (String) -> T,
  ): HttpResponse<T> {
    check(lifetime.isActive) { "The network client is closed." }
    val response =
      try {
        request(url, parameters)
      } catch (exception: Throwable) {
        currentCoroutineContext().ensureActive()
        return when {
          exception is CancellationException -> throw exception
          exception is HttpRequestTimeoutException ||
            exception is ConnectTimeoutException ||
            exception is SocketTimeoutException -> HttpResponse.Error.Timeout(exception)
          factory.isTransportFailure(exception) -> HttpResponse.Error.Transport(exception)
          else -> throw exception
        }
      }
    currentCoroutineContext().ensureActive()
    return response.decode(decode)
  }

  private fun <T : Any> Response.decode(decode: (String) -> T): HttpResponse<T> =
    when (statusCode) {
      in 200..299 ->
        try {
          HttpResponse.Success(decode(checkNotNull(body)), statusCode)
        } catch (exception: IllegalArgumentException) {
          HttpResponse.Error.Decoding(exception)
        }
      in 300..399 -> HttpResponse.Redirect(statusCode, location)
      401 -> HttpResponse.Error.Unauthenticated
      in 400..499 -> HttpResponse.Error.Client(statusCode)
      in 500..599 -> HttpResponse.Error.Server(statusCode)
      else -> HttpResponse.Error.UnexpectedStatus(statusCode)
    }

  private suspend fun request(url: String, parameters: Map<String, String>): Response =
    client
      .prepareGet(url) {
        url { parameters.forEach { (name, value) -> this.parameters.append(name, value) } }
      }
      .execute { response ->
        Response(
          statusCode = response.status.value,
          location = response.headers[HttpHeaders.Location],
          body = if (response.status.value in 200..299) response.bodyAsText() else null,
        )
      }

  private data class Response(val statusCode: Int, val location: String?, val body: String?)
}
