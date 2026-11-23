package software.ralf.storymile.podcast

import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import software.ralf.storymile.podcast.impl.fake.generated.resources.Res

/** Bundled podcasts for the app's Fake mode. */
object FakePodcasts {
  /** Feeds keyed by their Discover IDs, in the order used to seed the queue. */
  val feeds: Map<String, PodcastFeed> =
    mapOf(
      feed(
        id = "science",
        title = "Small Science",
        author = "Storymile Science",
        description = "Small discoveries in everyday life.",
        narrator = "Coral",
        recordings =
          listOf(
            Recording(number = 1, durationSeconds = 15),
            Recording(number = 2, durationSeconds = 16),
            Recording(number = 3, durationSeconds = 18),
          ),
      ),
      feed(
        id = "history",
        title = "History on Foot",
        author = "Storymile Stories",
        description = "Stories from the places around us.",
        narrator = "Daniel",
        recordings =
          listOf(
            Recording(number = 4, durationSeconds = 14),
            Recording(number = 5, durationSeconds = 16),
            Recording(number = 6, durationSeconds = 16),
          ),
      ),
      feed(
        id = "design",
        title = "Design Notes",
        author = "Storymile Studio",
        description = "How useful things take shape.",
        narrator = "Karen",
        recordings =
          listOf(
            Recording(number = 7, durationSeconds = 14, title = "Push or Pull"),
            Recording(number = 8, durationSeconds = 16, title = "Signs at a Glance"),
            Recording(number = 9, durationSeconds = 16, title = "Why Keys Click"),
            Recording(number = 10, durationSeconds = 23, title = "A Chair That Stacks"),
            Recording(number = 11, durationSeconds = 20, title = "What a Transit Map Leaves Out"),
          ),
      ),
    )

  /** The sample episodes in their initial queue order. */
  val queue: List<QueuedEpisode> =
    feeds.values.flatMap { feed ->
      feed.episodes.map { episode ->
        QueuedEpisode(
          feedUrl = feed.feedUrl,
          podcastTitle = feed.title,
          artworkUrl = episode.artworkUrl ?: feed.artworkUrl,
          episode = episode,
        )
      }
    }

  private val audioPaths = queue.associate {
    it.episode.enclosure.url to "files/test-episodes/${it.episode.id}.mp3"
  }

  /** Returns the bundled audio URI for [enclosureUrl], or null for a URL outside the catalog. */
  @OptIn(ExperimentalResourceApi::class)
  fun audioUri(enclosureUrl: String): String? = audioPaths[enclosureUrl]?.let { Res.getUri(it) }

  private fun feed(
    id: String,
    title: String,
    author: String,
    description: String,
    narrator: String,
    recordings: List<Recording>,
  ): Pair<String, PodcastFeed> =
    "sample-$id" to
      PodcastFeed(
        feedUrl = "https://example.invalid/podcasts/$id.xml",
        title = title,
        description = description,
        author = author,
        artworkUrl = artworkUri(id),
        link = "https://example.invalid/podcasts/$id",
        episodes =
          recordings.map { recording ->
            val episodeId = "test-episode-${recording.number}"
            PodcastEpisode(
              id = episodeId,
              title = recording.title,
              description = "A sample episode of $title.",
              author = narrator,
              artworkUrl = artworkUri(episodeId),
              publishedAt = "Tue, 01 Sep 2026 12:00:00 GMT",
              duration = recording.durationSeconds.seconds,
              enclosure =
                PodcastEnclosure(
                  url = "https://storymile.invalid/test-episodes/$episodeId.mp3",
                  type = "audio/mpeg",
                  length = null,
                ),
            )
          },
      )

  @OptIn(ExperimentalResourceApi::class)
  private fun artworkUri(id: String): String? =
    try {
      Res.getUri("files/artwork/$id.jpg")
    } catch (_: MissingResourceException) {
      // Android host tests can read the catalog without packaged assets.
      null
    } catch (_: IllegalStateException) {
      // Compose also checks the instrumentation context, which host tests lack.
      null
    }

  private data class Recording(
    val number: Int,
    val durationSeconds: Int,
    val title: String = "Test episode $number",
  )
}
