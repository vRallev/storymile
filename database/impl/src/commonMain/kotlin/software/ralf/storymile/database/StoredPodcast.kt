package software.ralf.storymile.database

import androidx.room3.Embedded
import androidx.room3.Relation
import software.ralf.storymile.podcast.PodcastFeed

data class StoredPodcast(
  @Embedded val show: ShowEntity,
  @Relation(parentColumns = ["id"], entityColumns = ["showId"]) val episodes: List<EpisodeEntity>,
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
          .filter { it.feedPosition != null }
          .sortedBy { it.feedPosition }
          .map { it.toPodcastEpisode() },
    )
}
