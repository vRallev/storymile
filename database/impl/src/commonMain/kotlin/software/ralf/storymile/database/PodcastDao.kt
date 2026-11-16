package software.ralf.storymile.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
  @Transaction
  @Query("SELECT * FROM shows WHERE isFollowed ORDER BY feedUrl")
  fun observe(): Flow<List<StoredPodcast>>

  @Query("SELECT id FROM shows WHERE feedUrl = :feedUrl")
  suspend fun findShowId(feedUrl: String): Long?

  @Query("SELECT * FROM shows WHERE feedUrl = :feedUrl")
  suspend fun findShow(feedUrl: String): ShowEntity?

  @Insert suspend fun insertShow(show: ShowEntity): Long

  @Update suspend fun updateShow(show: ShowEntity)

  @Query("UPDATE shows SET isFollowed = 0 WHERE id = :showId") suspend fun clearFollow(showId: Long)

  @Query(
    "DELETE FROM shows WHERE id = :showId AND NOT isFollowed AND NOT EXISTS (SELECT 1 FROM episodes WHERE showId = :showId)",
  )
  suspend fun deleteEmptyUnfollowedShow(showId: Long)
}
