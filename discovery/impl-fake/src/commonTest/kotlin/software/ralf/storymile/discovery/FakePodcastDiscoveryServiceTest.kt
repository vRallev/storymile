package software.ralf.storymile.discovery

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

class FakePodcastDiscoveryServiceTest {
  @Test
  fun `fake popular returns all sample podcasts in a stable order`() = runTest {
    val service: PodcastDiscoveryService =
      FakePodcastDiscoveryService(DiscoveryPodcastFixtures.feeds)

    val first = service.popular()
    assertThat(first)
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.map { podcast -> podcast.title } }
      .isEqualTo(listOf("Small Science", "History on Foot", "Design Notes"))
    assertThat(service.popular()).isEqualTo(first)
  }

  @Test
  fun `fake search matches titles and authors without case or surrounding whitespace`() = runTest {
    val service: PodcastDiscoveryService =
      FakePodcastDiscoveryService(DiscoveryPodcastFixtures.feeds)

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
    val controller: RuntimeModeController = FakeRuntimeModeController(RuntimeMode.Fake)
    val fake = lazy { FakePodcastDiscoveryService(DiscoveryPodcastFixtures.feeds) }
    val service: PodcastDiscoveryService =
      PodcastDiscoveryServiceImpl(controller, lazy { error("Real service constructed") }, fake)

    assertThat(service.search("science"))
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.single().title }
      .isEqualTo("Small Science")
  }

  @Test
  fun `fake popular never constructs the real service`() = runTest {
    val controller: RuntimeModeController = FakeRuntimeModeController(RuntimeMode.Fake)
    val fake = lazy { FakePodcastDiscoveryService(DiscoveryPodcastFixtures.feeds) }
    val service: PodcastDiscoveryService =
      PodcastDiscoveryServiceImpl(controller, lazy { error("Real service constructed") }, fake)

    assertThat(service.popular())
      .isInstanceOf<HttpResponse.Success<List<DiscoveredPodcast>>>()
      .transform { it.data.map { podcast -> podcast.title } }
      .isEqualTo(listOf("Small Science", "History on Foot", "Design Notes"))
  }
}
