package software.ralf.storymile.discovery

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.util.testing.FakeDevice

class PodcastDiscoveryServiceTest {
  @Test
  fun `search trims the query and requests podcasts from the device country catalog`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastDiscoveryService = ApplePodcastDiscoveryService(client, FakeDevice("DE"))

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
          "country" to "DE",
          "limit" to "200",
        ),
      )
  }

  @Test
  fun `lookup resolves the requested chart ID to its feed URL`() = runTest {
    val client =
      LocalHttpClient(
        body =
          """{"results":[
      {"collectionId":123,"collectionName":"Chart Result","feedUrl":"https://feeds.test/show"},
      {"collectionId":456,"collectionName":"Another Result"}
    ]}""",
      )
    val service: PodcastDiscoveryService = ApplePodcastDiscoveryService(client, FakeDevice("DE"))
    assertThat(service.lookup("123"))
      .isEqualTo(
        HttpResponse.Success(
          listOf(
            DiscoveredPodcast("123", "Chart Result", null, "https://feeds.test/show", null, null),
          ),
          200,
        ),
      )
    assertThat(client.requestedUrl).isEqualTo("https://itunes.apple.com/lookup")
    assertThat(client.requestedParameters)
      .isEqualTo(mapOf("id" to "123", "entity" to "podcast", "country" to "US"))
    assertThat(service.lookup("789")).isEqualTo(HttpResponse.Success(emptyList(), 200))
  }

  @Test
  fun `popular requests the top 200 podcasts from the US chart`() = runTest {
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
    val service: PodcastDiscoveryService = ApplePodcastDiscoveryService(client, FakeDevice())

    assertThat(service.popular())
      .isEqualTo(
        HttpResponse.Success(
          listOf(DiscoveredPodcast("123", "Popular Result", null, null, null, null)),
          statusCode = 200,
        ),
      )
    assertThat(client.requestedUrl)
      .isEqualTo("https://itunes.apple.com/us/rss/toppodcasts/limit=200/json")
    assertThat(client.requestedParameters).isEqualTo(emptyMap())
  }

  @Test
  fun `search reads the current country and falls back to US when unavailable`() = runTest {
    val client = LocalHttpClient()
    val device = FakeDevice("CA")
    val service: PodcastDiscoveryService = ApplePodcastDiscoveryService(client, device)

    service.search("science")
    assertThat(client.requestedParameters["country"]).isEqualTo("CA")

    device.countryCode = "GB"
    service.search("science")
    assertThat(client.requestedParameters["country"]).isEqualTo("GB")

    device.countryCode = null
    service.search("science")
    assertThat(client.requestedParameters["country"]).isEqualTo("US")
  }

  @Test
  fun `blank queries return no podcasts without making a request`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastDiscoveryService = ApplePodcastDiscoveryService(client, FakeDevice())

    assertThat(service.search(" \n\t "))
      .isEqualTo(HttpResponse.Success(emptyList(), statusCode = 200))
    assertThat(client.requestedUrl).isNull()
  }

  @Test
  fun `catalog failures reach callers unchanged`() = runTest {
    val failure = HttpResponse.Error.Server(503)
    val service: PodcastDiscoveryService =
      ApplePodcastDiscoveryService(LocalHttpClient(error = failure), FakeDevice())

    assertThat(service.search("science")).isSameInstanceAs(failure)
    assertThat(service.popular()).isSameInstanceAs(failure)
    assertThat(service.lookup("123")).isSameInstanceAs(failure)
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

  @Test
  fun `fake search never constructs the real service`() = runTest {
    val controller: RuntimeModeController =
      object : RuntimeModeController {
        override val mode: RuntimeMode = RuntimeMode.Fake

        override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
      }
    val fake = lazy { FakePodcastDiscoveryService() }
    val service: PodcastDiscoveryService =
      PodcastDiscoveryServiceImpl(controller, lazy { error("Real service constructed") }, fake)

    assertThat(service.search("science"))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Small Science")
  }

  @Test
  fun `fake popular never constructs the real service`() = runTest {
    val controller: RuntimeModeController =
      object : RuntimeModeController {
        override val mode: RuntimeMode = RuntimeMode.Fake

        override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
      }
    val fake = lazy { FakePodcastDiscoveryService() }
    val service: PodcastDiscoveryService =
      PodcastDiscoveryServiceImpl(controller, lazy { error("Real service constructed") }, fake)

    assertThat(service.popular())
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.map { podcast -> podcast.title } }
      .isEqualTo(listOf("Small Science", "History on Foot", "Design Notes"))
  }
}
