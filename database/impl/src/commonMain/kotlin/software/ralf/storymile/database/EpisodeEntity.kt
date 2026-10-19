package software.ralf.storymile.database

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import software.ralf.storymile.podcast.PodcastEnclosure

@Entity(
  tableName = "episodes",
  primaryKeys = ["feedUrl", "id"],
  foreignKeys =
    [
      ForeignKey(
        entity = ShowEntity::class,
        parentColumns = ["feedUrl"],
        childColumns = ["feedUrl"],
        onDelete = ForeignKey.CASCADE,
      ),
    ],
  indices = [Index("feedUrl")],
)
data class EpisodeEntity(
  val feedUrl: String,
  val id: String,
  val position: Int,
  val title: String,
  val description: String?,
  val author: String?,
  val artworkUrl: String?,
  val publishedAt: String?,
  val durationMillis: Long?,
  @Embedded val enclosure: PodcastEnclosure,
)
