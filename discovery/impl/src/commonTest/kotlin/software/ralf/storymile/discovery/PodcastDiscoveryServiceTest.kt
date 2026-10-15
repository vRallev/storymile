package software.ralf.storymile.discovery

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

class PodcastDiscoveryServiceTest {
  @Test
  fun `search trims the query and requests podcasts from the US catalog`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastDiscoveryService = ApplePodcastDiscoveryService(client)

    assertThat(service.search("  science & history  "))
      .isEqualTo(
        HttpResponse.Success(
          listOf(DiscoveredPodcast("123", "Catalog Result", null, null, null, null)),
          statusCode = 200,
        ),
      )
    assertThat(client.requestedUrl).isEqualTo("https://itunes.apple.com/search")
    assertThat(client.requestedParameters)
      .isEqualTo(
        mapOf(
          "term" to "science & history",
          "media" to "podcast",
          "entity" to "podcast",
          "country" to "US",
          "limit" to "200",
        ),
      )
  }

  @Test
  fun `popular requests the top 25 podcasts from the US chart`() = runTest {
    val client =
      LocalHttpClient(
        body = """{"feed":{"results":[{"id":"123","name":"Popular Result"}]}}""",
      )
    val service: PodcastDiscoveryService = ApplePodcastDiscoveryService(client)

    assertThat(service.popular())
      .isEqualTo(
        HttpResponse.Success(
          listOf(DiscoveredPodcast("123", "Popular Result", null, null, null, null)),
          statusCode = 200,
        ),
      )
    assertThat(client.requestedUrl)
      .isEqualTo("https://rss.marketingtools.apple.com/api/v2/us/podcasts/top/25/podcasts.json")
    assertThat(client.requestedParameters).isEqualTo(emptyMap())
  }

  @Test
  fun `blank queries return no podcasts without making a request`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastDiscoveryService = ApplePodcastDiscoveryService(client)

    assertThat(service.search(" \n\t "))
      .isEqualTo(HttpResponse.Success(emptyList(), statusCode = 200))
    assertThat(client.requestedUrl).isNull()
  }

  @Test
  fun `catalog failures reach callers unchanged`() = runTest {
    val failure = HttpResponse.Error.Server(503)
    val service: PodcastDiscoveryService =
      ApplePodcastDiscoveryService(LocalHttpClient(error = failure))

    assertThat(service.search("science")).isSameInstanceAs(failure)
    assertThat(service.popular()).isSameInstanceAs(failure)
  }

  @Test
  fun `fake popular returns all sample podcasts in a stable order`() = runTest {
    val service: PodcastDiscoveryService = FakePodcastDiscoveryService()

    val first = service.popular()
    assertThat(first)
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.map { podcast -> podcast.title } }
      .isEqualTo(listOf("Small Science", "History on Foot", "Design Notes"))
    assertThat(service.popular()).isEqualTo(first)
  }

  @Test
  fun `fake search matches titles and authors without case or surrounding whitespace`() = runTest {
    val service: PodcastDiscoveryService = FakePodcastDiscoveryService()

    assertThat(service.search(" SCIENCE "))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.map { podcast -> podcast.title } }
      .isEqualTo(listOf("Small Science"))
    assertThat(service.search("studio"))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.map { podcast -> podcast.title } }
      .isEqualTo(listOf("Design Notes"))
    assertThat(service.search(" ")).isEqualTo(HttpResponse.Success(emptyList(), statusCode = 200))
    assertThat(service.search("no matching sample"))
      .isEqualTo(HttpResponse.Success(emptyList(), statusCode = 200))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `search waits for the saved mode and fake mode never constructs the real service`() =
    runTest {
      val savedMode = CompletableDeferred<RuntimeMode>()
      val controller: RuntimeModeController =
        object : RuntimeModeController {
          override suspend fun mode(): RuntimeMode = savedMode.await()

          override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
        }
      val fake = lazy { FakePodcastDiscoveryService() }
      val service: PodcastDiscoveryService =
        PodcastDiscoveryServiceImpl(controller, lazy { error("Real service constructed") }, fake)

      val result = async { service.search("science") }
      runCurrent()

      assertThat(result.isCompleted).isFalse()
      assertThat(fake.isInitialized()).isFalse()
      savedMode.complete(RuntimeMode.Fake)

      assertThat(result.await())
        .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
        .transform { it.data.single().title }
        .isEqualTo("Small Science")
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `popular waits for the saved mode and fake mode never constructs the real service`() =
    runTest {
      val savedMode = CompletableDeferred<RuntimeMode>()
      val controller: RuntimeModeController =
        object : RuntimeModeController {
          override suspend fun mode(): RuntimeMode = savedMode.await()

          override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
        }
      val fake = lazy { FakePodcastDiscoveryService() }
      val service: PodcastDiscoveryService =
        PodcastDiscoveryServiceImpl(controller, lazy { error("Real service constructed") }, fake)

      val result = async { service.popular() }
      runCurrent()

      assertThat(result.isCompleted).isFalse()
      assertThat(fake.isInitialized()).isFalse()
      savedMode.complete(RuntimeMode.Fake)

      assertThat(result.await())
        .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
        .transform { it.data.map { podcast -> podcast.title } }
        .isEqualTo(listOf("Small Science", "History on Foot", "Design Notes"))
    }
}
