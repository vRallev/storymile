package software.ralf.storymile.podcast

import kotlin.time.Duration.Companion.seconds

internal object FakePodcastAudio {
  private val durations = listOf(15.seconds, 16.seconds, 18.seconds)

  val queue =
    (1..3).map { number ->
      QueuedEpisode(
        feedUrl = "https://storymile.invalid/test-episodes.xml",
        podcastTitle = "Storymile test episodes",
        artworkUrl = null,
        episode =
          PodcastEpisode(
            id = "test-episode-$number",
            title = "Test episode $number",
            description = null,
            author = null,
            artworkUrl = null,
            publishedAt = null,
            duration = duration(number),
            enclosure = enclosure(number),
          ),
      )
    }

  fun duration(number: Int) = durations[number - 1]

  fun enclosure(number: Int): PodcastEnclosure =
    PodcastEnclosure(
      url = "https://storymile.invalid/test-episodes/test-episode-$number.mp3",
      type = "audio/mpeg",
      length = null,
    )
}
