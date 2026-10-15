package software.ralf.storymile.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.ktor.client.HttpClient as KtorClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import io.ktor.client.engine.js.JsError

@ContributesBinding(AppScope::class)
class WasmJsKtorClientFactory : KtorClientFactory() {
  override fun create(configure: HttpClientConfig<*>.() -> Unit): KtorClient =
    KtorClient(Js) { configure(this) }

  override fun isTransportFailure(cause: Throwable): Boolean =
    super.isTransportFailure(cause) || cause is JsError || cause.cause is JsError
}
