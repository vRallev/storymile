package software.ralf.storymile.discovery

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController
import software.ralf.storymile.util.Device
import software.ralf.storymile.util.testing.FakeDevice

class PodcastDiscoveryOverrideInjectionTest {
  @Test
  fun `optional binding serves local samples in fake mode without a request`() = runTest {
    val client = RecordingHttpClient()
    val service: PodcastDiscoveryService = graph(mode = RuntimeMode.Fake, client = client).service

    assertThat(service.popular())
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.map { podcast -> podcast.title } }
      .isEqualTo(listOf("Small Science", "History on Foot", "Design Notes"))
    assertThat(client.requestedUrl).isNull()
  }

  @Test
  fun `optional binding delegates to Apple in real mode`() = runTest {
    val client = RecordingHttpClient()
    val service: PodcastDiscoveryService = graph(mode = RuntimeMode.Real, client = client).service

    assertThat(service.search("science"))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Catalog Result")
    assertThat(client.requestedUrl).isEqualTo("https://itunes.apple.com/search")
  }

  private fun graph(mode: RuntimeMode, client: HttpClient): Graph =
    createGraphFactory<Graph.Factory>()
      .create(
        controller = FakeRuntimeModeController(mode),
        client = client,
        device = FakeDevice(),
        feeds = DiscoveryPodcastFixtures.feeds,
      )

  @DependencyGraph(AppScope::class)
  interface Graph {
    val service: PodcastDiscoveryService

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides controller: RuntimeModeController,
        @Provides client: HttpClient,
        @Provides device: Device,
        @Provides @Named("fakePodcastFeeds") feeds: Map<String, PodcastFeed>,
      ): Graph
    }
  }

  private class RecordingHttpClient : HttpClient {
    var requestedUrl: String? = null
      private set

    override suspend fun <T : Any> get(
      url: String,
      parameters: Map<String, String>,
      decode: (String) -> T,
    ): HttpResponse<T> {
      requestedUrl = url
      return HttpResponse.Success(
        data = decode("""{"results":[{"collectionId":123,"collectionName":"Catalog Result"}]}"""),
        statusCode = 200,
      )
    }
  }
}
