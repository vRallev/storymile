package software.ralf.storymile.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.ktor.client.HttpClient as KtorClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

@ContributesBinding(AppScope::class)
class IosKtorClientFactory : KtorClientFactory() {
  override fun create(configure: HttpClientConfig<*>.() -> Unit): KtorClient =
    KtorClient(Darwin) { configure(this) }
}
