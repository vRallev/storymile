package software.ralf.storymile.http

import io.ktor.client.HttpClient as KtorClient
import io.ktor.client.HttpClientConfig
import kotlinx.io.IOException

/** Creates a client that owns its platform engine. */
abstract class KtorClientFactory {
  abstract fun create(configure: HttpClientConfig<*>.() -> Unit): KtorClient

  open fun isTransportFailure(cause: Throwable): Boolean = cause is IOException
}
