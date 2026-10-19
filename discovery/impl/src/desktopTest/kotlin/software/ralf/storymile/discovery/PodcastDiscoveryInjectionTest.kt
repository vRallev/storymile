package software.ralf.storymile.discovery

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.discovery.presentation.TestPodcastFeeds
import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.util.Device
import software.ralf.storymile.util.testing.FakeDevice

class PodcastDiscoveryInjectionTest {
  @Test
  fun `repository cache is shared within a graph and resets in a new app session`() = runTest {
    val client = LocalHttpClient(body = """{"feed":{"entry":[]}}""")
    val graph = graph(RuntimeMode.Real, client)
    val repository: PodcastDiscoveryRepository = graph.repository

    assertThat(graph.repository).isSameInstanceAs(repository)
    repository.popular.test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(emptyList()))
    }
    graph.repository.popular.test {
      assertThat(awaitItem()).isEqualTo(State.Success(emptyList()))
    }
    assertThat(client.requestCount).isEqualTo(1)

    graph(RuntimeMode.Real, client).repository.popular.test {
      assertThat(awaitItem()).isEqualTo(State.Loading)
      assertThat(awaitItem()).isEqualTo(State.Success(emptyList()))
    }
    assertThat(client.requestCount).isEqualTo(2)
  }

  @Test
  fun `graph binding selects the real catalog service in real mode`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastDiscoveryService = graph(RuntimeMode.Real, client).service

    assertThat(service.search("science"))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Catalog Result")
    assertThat(client.requestedUrl).isEqualTo("https://itunes.apple.com/search")
    assertThat(client.requestedParameters["country"]).isEqualTo("DE")
  }

  @Test
  fun `graph binding selects the chart service in real mode`() = runTest {
    val client =
      LocalHttpClient(
        body =
          """
          {"feed":{"entry":[{
            "id":{"attributes":{"im:id":"123"}},"im:name":{"label":"Popular Result"}
          }]}}
          """
            .trimIndent(),
      )
    val service: PodcastDiscoveryService = graph(RuntimeMode.Real, client).service

    assertThat(service.popular())
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Popular Result")
    assertThat(client.requestedUrl)
      .isEqualTo("https://itunes.apple.com/us/rss/toppodcasts/limit=200/json")
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

  private fun TestScope.graph(mode: RuntimeMode, client: HttpClient): Graph =
    createGraphFactory<Graph.Factory>()
      .create(
        controller =
          object : RuntimeModeController {
            override suspend fun mode(): RuntimeMode = mode

            override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
          },
        client = client,
        device = FakeDevice("DE"),
        coroutineScope = backgroundScope,
        feeds = TestPodcastFeeds(),
      )

  @DependencyGraph(AppScope::class)
  interface Graph {
    val service: PodcastDiscoveryService
    val repository: PodcastDiscoveryRepository

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides controller: RuntimeModeController,
        @Provides client: HttpClient,
        @Provides device: Device,
        @Provides @ForScope(AppScope::class) coroutineScope: CoroutineScope,
        @Provides feeds: PodcastFeedRepository,
      ): Graph
    }
  }
}
