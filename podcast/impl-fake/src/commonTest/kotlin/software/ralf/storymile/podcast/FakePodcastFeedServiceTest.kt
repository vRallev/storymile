package software.ralf.storymile.podcast

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

class FakePodcastFeedServiceTest {
  @Test
  fun `fake feeds match discovery samples and stay deterministic`() = runTest {
    val service: PodcastFeedService = FakePodcastFeedService()

    mapOf(
        "science" to Triple(first = "Small Science", second = "Coral", third = 1..3),
        "history" to Triple(first = "History on Foot", second = "Daniel", third = 4..6),
        "design" to Triple(first = "Design Notes", second = "Karen", third = 7..11),
      )
      .forEach { (id, sample) ->
        val (title, narrator, recordings) = sample
        val feedUrl = "https://example.invalid/podcasts/$id.xml"
        val first = service.load(feedUrl)
        val feed =
          assertThat(first).isInstanceOf<HttpResponse.Success<PodcastFeed>>().transform { it.data }
        feed.transform { it.title }.isEqualTo(title)
        feed
          .transform { it.episodes.map { episode -> episode.author }.distinct() }
          .isEqualTo(listOf(narrator))
        feed
          .transform { it.episodes.map { episode -> episode.enclosure.url } }
          .isEqualTo(
            recordings.map { "https://storymile.invalid/test-episodes/test-episode-$it.mp3" },
          )
        assertThat(service.load(" $feedUrl ")).isEqualTo(first)
      }
    assertThat(service.load("https://example.invalid/podcasts/missing.xml"))
      .isEqualTo(HttpResponse.Error.Client(404))
  }

  @Test
  fun `fake feeds never construct the real service`() = runTest {
    val controller: RuntimeModeController = FakeRuntimeModeController(RuntimeMode.Fake)
    val fake = lazy { FakePodcastFeedService() }
    val service: PodcastFeedService =
      PodcastFeedServiceImpl(
        runtimeModeController = controller,
        real = lazy { error("Real service constructed") },
        fake = fake,
      )

    assertThat(service.load("https://example.invalid/podcasts/science.xml"))
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.title }
      .isEqualTo("Small Science")
  }
}
