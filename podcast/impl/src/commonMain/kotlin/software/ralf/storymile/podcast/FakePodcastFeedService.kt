package software.ralf.storymile.podcast

import dev.zacsweers.metro.Inject
import kotlin.time.Duration.Companion.minutes
import software.ralf.storymile.http.HttpResponse

@Inject
class FakePodcastFeedService : PodcastFeedService {
  private val feeds =
    listOf(
        feed(
          "science",
          "Small Science",
          "Storymile Science",
          "Small discoveries in everyday life.",
        ),
        feed(
          "history",
          "History on Foot",
          "Storymile Stories",
          "Stories from the places around us.",
        ),
        feed("design", "Design Notes", "Storymile Studio", "How useful things take shape."),
      )
      .associateBy { it.feedUrl }

  override suspend fun load(feedUrl: String): HttpResponse<PodcastFeed> {
    val url =
      requireNotNull(RssUrl.normalize(feedUrl.trim())) {
        "The feed URL must be an absolute HTTP(S) URL without credentials."
      }
    val feed = feeds[url] ?: return HttpResponse.Error.Client(404)
    return HttpResponse.Success(feed, statusCode = 200)
  }

  private fun feed(id: String, title: String, author: String, description: String): PodcastFeed =
    PodcastFeed(
      feedUrl = "https://example.invalid/podcasts/$id.xml",
      title = title,
      description = description,
      author = author,
      artworkUrl = null,
      link = "https://example.invalid/podcasts/$id",
      episodes =
        (1..2).map { number ->
          PodcastEpisode(
            id = "sample-$id-$number",
            title = "$title: Episode $number",
            description = "A sample episode of $title.",
            author = author,
            artworkUrl = null,
            publishedAt = "Tue, 01 Sep 2026 12:00:00 GMT",
            duration = (number * 10).minutes,
            enclosure =
              PodcastEnclosure(
                url = "https://example.invalid/podcasts/$id-$number.mp3",
                type = "audio/mpeg",
                length = null,
              ),
          )
        },
    )
}
