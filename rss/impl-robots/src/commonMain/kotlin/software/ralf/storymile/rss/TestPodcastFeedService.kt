package software.ralf.storymile.rss

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlin.time.Duration.Companion.minutes
import software.ralf.storymile.http.HttpResponse

/** Supplies local episode metadata without fetching feeds or artwork. */
@ContributesBinding(AppScope::class, replaces = [PodcastFeedServiceImpl::class])
class TestPodcastFeedService : PodcastFeedService {
  override suspend fun load(feedUrl: String): HttpResponse<PodcastFeed> {
    val title =
      when (feedUrl.substringAfterLast('/')) {
        "quiet-current" -> "The Quiet Current"
        "field-notes" -> "Field Notes"
        else -> "Small Wonders"
      }
    return HttpResponse.Success(
      data =
        PodcastFeed(
          feedUrl = feedUrl,
          title = title,
          description = null,
          author = null,
          artworkUrl = null,
          link = null,
          episodes =
            List(3) { index ->
              PodcastEpisode(
                id = "$index",
                title = "$title episode ${index + 1}",
                description = null,
                author = null,
                artworkUrl = null,
                publishedAt = null,
                duration = 30.minutes,
                enclosure =
                  PodcastEnclosure(
                    url = "https://audio.test/$index.mp3",
                    type = "audio/mpeg",
                    length = null,
                  ),
              )
            },
        ),
      statusCode = 200,
    )
  }
}
