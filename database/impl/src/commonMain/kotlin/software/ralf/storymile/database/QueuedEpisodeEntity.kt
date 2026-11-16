package software.ralf.storymile.database

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
  tableName = "queued_episodes",
  foreignKeys =
    [
      ForeignKey(
        entity = EpisodeEntity::class,
        parentColumns = ["id"],
        childColumns = ["episodeId"],
        onDelete = ForeignKey.RESTRICT,
      ),
    ],
  indices = [Index(value = ["episodeId"], unique = true), Index("position")],
)
data class QueuedEpisodeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val episodeId: Long,
  val position: Long,
)
