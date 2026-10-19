package software.ralf.storymile.database

import androidx.room3.Embedded
import androidx.room3.Relation
import kotlin.time.Duration.Companion.milliseconds
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed

data class StoredPodcast(
  @Embedded val show: ShowEntity,
  @Relation(parentColumns = ["feedUrl"], entityColumns = ["feedUrl"])
  val episodes: List<EpisodeEntity>,
) {
  fun toPodcast(): PodcastFeed =
    PodcastFeed(
      feedUrl = show.feedUrl,
      title = show.title,
      description = show.description,
      author = show.author,
      artworkUrl = show.artworkUrl,
      link = show.link,
      episodes =
        episodes
          .sortedBy { it.position }
          .map { episode ->
            PodcastEpisode(
              id = episode.id,
              title = episode.title,
              description = episode.description,
              author = episode.author,
              artworkUrl = episode.artworkUrl,
              publishedAt = episode.publishedAt,
              duration = episode.durationMillis?.milliseconds,
              enclosure = episode.enclosure,
            )
          },
    )
}
