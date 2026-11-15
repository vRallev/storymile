package software.ralf.storymile.database

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Duration.Companion.milliseconds
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueuedEpisode

@Entity(
  tableName = "queued_episodes",
  indices = [Index(value = ["feedUrl", "episodeId"], unique = true)],
)
data class QueuedEpisodeEntity(
  @PrimaryKey(autoGenerate = true) val position: Long = 0,
  val feedUrl: String,
  val podcastTitle: String,
  val artworkUrl: String?,
  val episodeId: String,
  val title: String,
  val description: String?,
  val author: String?,
  val episodeArtworkUrl: String?,
  val publishedAt: String?,
  val durationMillis: Long?,
  @Embedded val enclosure: PodcastEnclosure,
) {
  fun toQueuedEpisode(): QueuedEpisode =
    QueuedEpisode(
      feedUrl = feedUrl,
      podcastTitle = podcastTitle,
      artworkUrl = artworkUrl,
      episode =
        PodcastEpisode(
          id = episodeId,
          title = title,
          description = description,
          author = author,
          artworkUrl = episodeArtworkUrl,
          publishedAt = publishedAt,
          duration = durationMillis?.milliseconds,
          enclosure = enclosure,
        ),
    )

  companion object {
    fun from(queuedEpisode: QueuedEpisode): QueuedEpisodeEntity {
      val episode = queuedEpisode.episode
      return QueuedEpisodeEntity(
        feedUrl = queuedEpisode.feedUrl,
        podcastTitle = queuedEpisode.podcastTitle,
        artworkUrl = queuedEpisode.artworkUrl,
        episodeId = episode.id,
        title = episode.title,
        description = episode.description,
        author = episode.author,
        episodeArtworkUrl = episode.artworkUrl,
        publishedAt = episode.publishedAt,
        durationMillis = episode.duration?.inWholeMilliseconds,
        enclosure = episode.enclosure,
      )
    }
  }
}
