package software.ralf.storymile.discovery

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

class PodcastDiscoveryInjectionTest {
  @Test
  fun `graph binding selects the real catalog service in real mode`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastDiscoveryService = graph(RuntimeMode.Real, client).service

    assertThat(service.search("science"))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Catalog Result")
    assertThat(client.requestedUrl).isEqualTo("https://itunes.apple.com/search")
  }

  @Test
  fun `graph binding selects local samples in fake mode without a request`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastDiscoveryService = graph(RuntimeMode.Fake, client).service

    assertThat(service.search("science"))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Small Science")
    assertThat(client.requestedUrl).isNull()
  }

  private fun graph(mode: RuntimeMode, client: HttpClient): Graph =
    createGraphFactory<Graph.Factory>()
      .create(
        object : RuntimeModeController {
          override suspend fun mode(): RuntimeMode = mode

          override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
        },
        client,
      )

  @DependencyGraph(AppScope::class)
  interface Graph {
    val service: PodcastDiscoveryService

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides controller: RuntimeModeController,
        @Provides client: HttpClient,
      ): Graph
    }
  }
}
