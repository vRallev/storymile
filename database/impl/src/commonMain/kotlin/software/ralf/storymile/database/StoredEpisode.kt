package software.ralf.storymile.database

import androidx.room3.Embedded
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.podcast.SavedEpisode

data class StoredEpisode(
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

  fun toSavedEpisode(): SavedEpisode =
    SavedEpisode(
      feedUrl = feedUrl,
      podcastTitle = podcastTitle,
      artworkUrl = episode.artworkUrl ?: showArtworkUrl,
      episode = episode.toPodcastEpisode(),
    )
}
