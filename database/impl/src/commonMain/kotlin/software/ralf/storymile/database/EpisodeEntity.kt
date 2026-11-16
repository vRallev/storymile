package software.ralf.storymile.database

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Duration.Companion.milliseconds
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode

@Entity(
  tableName = "episodes",
  foreignKeys =
    [
      ForeignKey(
        entity = ShowEntity::class,
        parentColumns = ["id"],
        childColumns = ["showId"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
  indices = [Index(value = ["showId", "sourceEpisodeId"])],
)
data class EpisodeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val showId: Long,
  val sourceEpisodeId: String,
  val feedPosition: Int?,
  val title: String,
  val description: String?,
  val author: String?,
  val artworkUrl: String?,
  val publishedAt: String?,
  val durationMillis: Long?,
  @Embedded val enclosure: PodcastEnclosure,
) {
  fun toPodcastEpisode(): PodcastEpisode =
    PodcastEpisode(
      id = sourceEpisodeId,
      title = title,
      description = description,
      author = author,
      artworkUrl = artworkUrl,
      publishedAt = publishedAt,
      duration = durationMillis?.milliseconds,
      enclosure = enclosure,
    )
}
