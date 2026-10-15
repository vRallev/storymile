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
  fun `graph binding selects the chart service in real mode`() = runTest {
    val client =
      LocalHttpClient(
        body = """{"feed":{"results":[{"id":"123","name":"Popular Result"}]}}""",
      )
    val service: PodcastDiscoveryService = graph(RuntimeMode.Real, client).service

    assertThat(service.popular())
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Popular Result")
    assertThat(client.requestedUrl)
      .isEqualTo("https://rss.marketingtools.apple.com/api/v2/us/podcasts/top/25/podcasts.json")
  }

  @Test
  fun `graph binding selects local samples in fake mode without a request`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastDiscoveryService = graph(RuntimeMode.Fake, client).service

    assertThat(service.search("science"))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Small Science")
    assertThat(service.popular())
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.map { podcast -> podcast.title } }
      .isEqualTo(listOf("Small Science", "History on Foot", "Design Notes"))
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
