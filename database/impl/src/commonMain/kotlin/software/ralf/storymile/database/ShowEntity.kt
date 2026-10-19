package software.ralf.storymile.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "shows")
data class ShowEntity(
  @PrimaryKey val feedUrl: String,
  val title: String,
  val description: String?,
  val author: String?,
  val artworkUrl: String?,
  val link: String?,
)
