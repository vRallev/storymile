package software.ralf.storymile.database

import androidx.room3.Embedded
import software.ralf.storymile.podcast.QueuedEpisode

data class StoredQueuedEpisode(
  @Embedded val episode: EpisodeEntity,
  val feedUrl: String,
  val podcastTitle: String,
  val showArtworkUrl: String?,
) {
  fun toQueuedEpisode(): QueuedEpisode =
    QueuedEpisode(
      feedUrl = feedUrl,
      podcastTitle = podcastTitle,
      artworkUrl = episode.artworkUrl ?: showArtworkUrl,
      episode = episode.toPodcastEpisode(),
    )
}
