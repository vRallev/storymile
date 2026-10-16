package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlin.time.Duration.Companion.minutes
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.rss.PodcastEnclosure
import software.ralf.storymile.rss.PodcastEpisode
import software.ralf.storymile.rss.PodcastFeed
import software.ralf.storymile.rss.PodcastFeedService
import software.ralf.storymile.rss.PodcastFeedServiceImpl

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
      PodcastFeed(
        feedUrl,
        title,
        null,
        null,
        null,
        null,
        List(3) { index ->
          PodcastEpisode(
            "$index",
            "$title episode ${index + 1}",
            null,
            null,
            null,
            null,
            30.minutes,
            PodcastEnclosure("https://audio.test/$index.mp3", "audio/mpeg", null),
          )
        },
      ),
      200,
    )
  }
}
