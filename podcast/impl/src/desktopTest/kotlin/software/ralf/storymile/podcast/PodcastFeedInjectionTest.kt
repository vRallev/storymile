package software.ralf.storymile.podcast

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse

class PodcastFeedInjectionTest {
  @Test
  fun `graph binding requests the RSS feed by default`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastFeedService = graph(client).service

    assertThat(service.load("https://feeds.test/show.xml"))
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.title }
      .isEqualTo("Feed Result")
    assertThat(client.requestedUrls).isEqualTo(listOf("https://feeds.test/show.xml"))
  }

  private fun graph(client: HttpClient): Graph = createGraphFactory<Graph.Factory>().create(client)

  @DependencyGraph(AppScope::class)
  interface Graph {
    val service: PodcastFeedService

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(@Provides client: HttpClient): Graph
    }
  }
}
