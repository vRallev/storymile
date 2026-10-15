package software.ralf.storymile.http

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.ktor.client.HttpClient as KtorClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

@ContributesBinding(AppScope::class)
class DesktopKtorClientFactory : KtorClientFactory() {
  override fun create(configure: HttpClientConfig<*>.() -> Unit): KtorClient =
    KtorClient(OkHttp) {
      configure(this)
      engine {
        config {
          followRedirects(false)
          followSslRedirects(false)
        }
      }
    }
}
