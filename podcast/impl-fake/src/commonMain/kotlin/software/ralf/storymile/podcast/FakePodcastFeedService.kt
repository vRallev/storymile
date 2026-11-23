package software.ralf.storymile.podcast

import dev.zacsweers.metro.Inject
import software.ralf.storymile.http.HttpResponse

@Inject
class FakePodcastFeedService : PodcastFeedService {
  private val feeds = FakePodcasts.feeds.values.associateBy { it.feedUrl }

  override suspend fun load(feedUrl: String): HttpResponse<PodcastFeed> {
    val url =
      requireNotNull(RssUrl.normalize(feedUrl.trim())) {
        "The feed URL must be an absolute HTTP(S) URL without credentials."
      }
    val feed = feeds[url] ?: return HttpResponse.Error.Client(404)
    return HttpResponse.Success(feed, statusCode = 200)
  }
}
