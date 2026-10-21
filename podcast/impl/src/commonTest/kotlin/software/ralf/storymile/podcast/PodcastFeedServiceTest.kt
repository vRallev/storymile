package software.ralf.storymile.podcast

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

class PodcastFeedServiceTest {
  @Test
  fun `loads show details and episodes from a trimmed feed URL`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastFeedService = RssPodcastFeedService(client)

    val response = service.load("  https://feeds.test/show.xml?token=value  ")

    assertThat(response)
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data }
      .transform { Triple(it.title, it.description, it.episodes.single().title) }
      .isEqualTo(Triple("Feed Result", "A show about science.", "First episode"))
    assertThat(response)
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.feedUrl }
      .isEqualTo("https://feeds.test/show.xml?token=value")
    assertThat(client.requestedUrls).isEqualTo(listOf("https://feeds.test/show.xml?token=value"))
  }

  @Test
  fun `invalid feed URLs are rejected before requesting the network`() = runTest {
    val client = LocalHttpClient()
    val service: PodcastFeedService = RssPodcastFeedService(client)

    listOf(
        " ",
        "show.xml",
        "//feeds.test/show.xml",
        "ftp://feeds.test/show.xml",
        "https://user:password@feeds.test/show.xml",
        "https://user@feeds.test/show.xml",
        "https:///show.xml",
        "https://feeds.test:invalid/show.xml",
        "https://feeds.test/\\show.xml",
        "https://feeds.test/\nshow.xml",
      )
      .forEach { url ->
        assertFailure { service.load(url) }.isInstanceOf<IllegalArgumentException>()
      }
    assertThat(client.requestedUrls).isEmpty()
  }

  @Test
  fun `follows supported redirect statuses and preserves the final feed URL`() = runTest {
    val client =
      LocalHttpClient(
        responses =
          listOf(301, 302, 303, 307, 308)
            .mapIndexed { index, status ->
              "https://feeds.test/$index" to HttpResponse.Redirect(status, "/${index + 1}")
            }
            .toMap(),
      )
    val service: PodcastFeedService = RssPodcastFeedService(client)

    assertThat(service.load("https://feeds.test/0"))
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.feedUrl }
      .isEqualTo("https://feeds.test/5")
    assertThat(client.requestedUrls).isEqualTo((0..5).map { "https://feeds.test/$it" })
  }

  @Test
  fun `relative redirects replace old queries and resolve against each previous URL`() = runTest {
    val client =
      LocalHttpClient(
        responses =
          mapOf(
            "https://feeds.test/podcasts/show.xml?secret=old" to
              HttpResponse.Redirect(302, "../next.xml?token=new"),
            "https://feeds.test/next.xml?token=new" to HttpResponse.Redirect(307, "?token=last"),
            "https://feeds.test/next.xml?token=last" to
              HttpResponse.Redirect(308, "//cdn.test/final.xml"),
          ),
      )
    val service: PodcastFeedService = RssPodcastFeedService(client)

    assertThat(service.load("https://feeds.test/podcasts/show.xml?secret=old"))
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.feedUrl }
      .isEqualTo("https://cdn.test/final.xml")
    assertThat(client.requestedUrls)
      .isEqualTo(
        listOf(
          "https://feeds.test/podcasts/show.xml?secret=old",
          "https://feeds.test/next.xml?token=new",
          "https://feeds.test/next.xml?token=last",
          "https://cdn.test/final.xml",
        ),
      )
  }

  @Test
  fun `path redirects do not forward the original query`() = runTest {
    val client =
      LocalHttpClient(
        responses =
          mapOf(
            "https://feeds.test/show.xml?secret=old" to HttpResponse.Redirect(302, "next.xml"),
          ),
      )
    val service: PodcastFeedService = RssPodcastFeedService(client)

    assertThat(service.load("https://feeds.test/show.xml?secret=old"))
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.feedUrl }
      .isEqualTo("https://feeds.test/next.xml")
  }

  @Test
  fun `returns redirects without a safe destination without another request`() = runTest {
    listOf(
        null,
        "",
        "ftp://feeds.test/show.xml",
        "https://user@feeds.test/show.xml",
        "http://feeds.test/show.xml",
        "https:next.xml",
        "//user:pass@feeds.test/show.xml",
      )
      .forEach { target ->
        val redirect = HttpResponse.Redirect(302, target)
        val client = LocalHttpClient(responses = mapOf("https://feeds.test/show.xml" to redirect))
        val service: PodcastFeedService = RssPodcastFeedService(client)

        assertThat(service.load("https://feeds.test/show.xml")).isSameInstanceAs(redirect)
        assertThat(client.requestedUrls).isEqualTo(listOf("https://feeds.test/show.xml"))
      }
  }

  @Test
  fun `unsupported redirect statuses are returned without following Location`() = runTest {
    val redirect = HttpResponse.Redirect(304, "/next.xml")
    val client = LocalHttpClient(responses = mapOf("https://feeds.test/show.xml" to redirect))
    val service: PodcastFeedService = RssPodcastFeedService(client)

    assertThat(service.load("https://feeds.test/show.xml")).isSameInstanceAs(redirect)
    assertThat(client.requestedUrls).isEqualTo(listOf("https://feeds.test/show.xml"))
  }

  @Test
  fun `redirect loops preserve the last redirect`() = runTest {
    val last = HttpResponse.Redirect(302, "/a.xml")
    val client =
      LocalHttpClient(
        responses =
          mapOf(
            "https://feeds.test/a.xml" to HttpResponse.Redirect(301, "/b.xml"),
            "https://feeds.test/b.xml" to last,
          ),
      )
    val service: PodcastFeedService = RssPodcastFeedService(client)

    assertThat(service.load("https://feeds.test/a.xml")).isSameInstanceAs(last)
    assertThat(client.requestedUrls)
      .isEqualTo(listOf("https://feeds.test/a.xml", "https://feeds.test/b.xml"))
  }

  @Test
  fun `fragment-only redirects preserve query parameters and detect the same resource`() = runTest {
    val redirect = HttpResponse.Redirect(302, "#show")
    val client =
      LocalHttpClient(responses = mapOf("https://feeds.test/show.xml?token=value" to redirect))
    val service: PodcastFeedService = RssPodcastFeedService(client)

    assertThat(service.load("https://feeds.test/show.xml?token=value")).isSameInstanceAs(redirect)
    assertThat(client.requestedUrls).isEqualTo(listOf("https://feeds.test/show.xml?token=value"))
  }

  @Test
  fun `redirect chains stop after five hops`() = runTest {
    val last = HttpResponse.Redirect(302, "/6")
    val responses =
      (0..4).associate { "https://feeds.test/$it" to HttpResponse.Redirect(302, "/${it + 1}") } +
        ("https://feeds.test/5" to last)
    val client = LocalHttpClient(responses = responses)
    val service: PodcastFeedService = RssPodcastFeedService(client)

    assertThat(service.load("https://feeds.test/0")).isSameInstanceAs(last)
    assertThat(client.requestedUrls).isEqualTo((0..5).map { "https://feeds.test/$it" })
  }

  @Test
  fun `HTTP and transport failures reach callers unchanged`() = runTest {
    listOf(
        HttpResponse.Error.Client(404),
        HttpResponse.Error.Server(503),
        HttpResponse.Error.Unauthenticated,
        HttpResponse.Error.UnexpectedStatus(0),
        HttpResponse.Error.Transport(Exception("offline")),
        HttpResponse.Error.Timeout(Exception("timeout")),
      )
      .forEach { failure ->
        val service: PodcastFeedService =
          RssPodcastFeedService(
            LocalHttpClient(responses = mapOf("https://feeds.test/show.xml" to failure)),
          )
        assertThat(service.load("https://feeds.test/show.xml")).isSameInstanceAs(failure)
      }
  }

  @Test
  fun `malformed feeds become decoding failures`() = runTest {
    listOf("<rss>", "not xml").forEach { body ->
      val service: PodcastFeedService = RssPodcastFeedService(LocalHttpClient(body = body))

      assertThat(service.load("https://feeds.test/show.xml"), name = body)
        .isInstanceOf<HttpResponse.Error.Decoding>()
    }
  }

  @Test
  fun `cancellation and programming failures propagate`() = runTest {
    listOf(CancellationException("cancelled"), IllegalStateException("closed")).forEach { failure ->
      val service: PodcastFeedService = RssPodcastFeedService(LocalHttpClient(failure = failure))

      assertFailure { service.load("https://feeds.test/show.xml") }.isSameInstanceAs(failure)
    }
  }

  @Test
  fun `fake feeds match discovery samples and stay deterministic`() = runTest {
    val service: PodcastFeedService = FakePodcastFeedService()

    listOf("science" to "Small Science", "history" to "History on Foot", "design" to "Design Notes")
      .forEach { (id, title) ->
        val feedUrl = "https://example.invalid/podcasts/$id.xml"
        val first = service.load(feedUrl)
        assertThat(first)
          .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
          .transform { it.data.title }
          .isEqualTo(title)
        assertThat(first)
          .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
          .transform { it.data.episodes.size }
          .isEqualTo(2)
        assertThat(service.load(" $feedUrl ")).isEqualTo(first)
      }
    assertThat(service.load("https://example.invalid/podcasts/missing.xml"))
      .isEqualTo(HttpResponse.Error.Client(404))
  }

  @Test
  fun `fake feeds never construct the real service`() = runTest {
    val controller: RuntimeModeController =
      object : RuntimeModeController {
        override val mode: RuntimeMode = RuntimeMode.Fake

        override suspend fun switchMode(mode: RuntimeMode) = error("Not used")
      }
    val fake = lazy { FakePodcastFeedService() }
    val service: PodcastFeedService =
      PodcastFeedServiceImpl(
        controller,
        lazy { error("Real service constructed") },
        fake,
      )

    assertThat(service.load("https://example.invalid/podcasts/science.xml"))
      .isInstanceOf<HttpResponse.Success<PodcastFeed>>()
      .transform { it.data.title }
      .isEqualTo("Small Science")
  }
}
