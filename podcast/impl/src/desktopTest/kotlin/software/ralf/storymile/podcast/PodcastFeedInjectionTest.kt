package software.ralf.storymile.podcast

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

class PodcastFeedInjectionTest {
  @Test
  fun `graph binding requests the RSS feed in real mode`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastFeedService = graph(RuntimeMode.Real, client).service

    assertThat(service.load("https://feeds.test/show.xml"))
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.title }
      .isEqualTo("Feed Result")
    assertThat(client.requestedUrls).isEqualTo(listOf("https://feeds.test/show.xml"))
  }

  @Test
  fun `graph binding returns sample RSS details without a request in fake mode`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastFeedService = graph(RuntimeMode.Fake, client).service

    assertThat(service.load("https://example.invalid/podcasts/science.xml"))
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.title }
      .isEqualTo("Small Science")
    assertThat(client.requestedUrls).isEmpty()
  }

  @Test
  fun `repository binding shares feeds from the selected service`() = runTest {
    val client = LocalHttpClient()
    val repository: PodcastFeedRepository = graph(RuntimeMode.Fake, client).repository
    repository.feed("https://example.invalid/podcasts/science.xml").test {
      assertThat(awaitItem()).isEqualTo(PodcastFeedRepository.State.Loading)
      assertThat(awaitItem())
        .isInstanceOf<PodcastFeedRepository.State.Success>()
        .transform { it.feed.title }
        .isEqualTo("Small Science")
    }
    assertThat(client.requestedUrls).isEmpty()
  }

  private fun TestScope.graph(mode: RuntimeMode, client: HttpClient): Graph =
    createGraphFactory<Graph.Factory>()
      .create(
        object : RuntimeModeController {
          override suspend fun mode(): RuntimeMode = mode

          override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
        },
        client,
        backgroundScope,
      )

  @DependencyGraph(AppScope::class)
  interface Graph {
    val service: PodcastFeedService
    val repository: PodcastFeedRepository

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides controller: RuntimeModeController,
        @Provides client: HttpClient,
        @Provides @ForScope(AppScope::class) coroutineScope: CoroutineScope,
      ): Graph
    }
  }
}
