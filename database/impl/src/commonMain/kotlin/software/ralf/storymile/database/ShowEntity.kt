package software.ralf.storymile.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "shows", indices = [Index(value = ["feedUrl"], unique = true)])
data class ShowEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val feedUrl: String,
  val title: String,
  val description: String?,
  val author: String?,
  val artworkUrl: String?,
  val link: String?,
  @ColumnInfo(defaultValue = "0") val isFollowed: Boolean = false,
)
