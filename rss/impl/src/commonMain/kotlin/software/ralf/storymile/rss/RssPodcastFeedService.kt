package software.ralf.storymile.rss

import dev.zacsweers.metro.Inject
import software.ralf.storymile.http.HttpClient
import software.ralf.storymile.http.HttpResponse

@Inject
class RssPodcastFeedService(private val client: HttpClient) : PodcastFeedService {
  override suspend fun load(feedUrl: String): HttpResponse<PodcastFeed> {
    var url =
      requireNotNull(RssUrl.normalize(feedUrl.trim())) {
        "The feed URL must be an absolute HTTP(S) URL without credentials."
      }
    val visited = mutableSetOf(url)
    var redirects = 0
    while (true) {
      val response = client.get(url) { RssFeedParser().decode(it, feedUrl = url) }
      if (response !is HttpResponse.Redirect) {
        return response
      }
      if (redirects == 5 || response.statusCode !in listOf(301, 302, 303, 307, 308)) {
        return response
      }
      val destination = response.location?.let { RssUrl.resolve(url, it) } ?: return response
      if (
        (url.startsWith("https:", ignoreCase = true) &&
          destination.startsWith("http:", ignoreCase = true)) || !visited.add(destination)
      ) {
        return response
      }
      url = destination
      redirects++
    }
  }
}
