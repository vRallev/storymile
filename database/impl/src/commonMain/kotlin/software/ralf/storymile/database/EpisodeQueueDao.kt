package software.ralf.storymile.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeQueueDao {
  @Query("SELECT * FROM queued_episodes ORDER BY position")
  fun observe(): Flow<List<QueuedEpisodeEntity>>

  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(episode: QueuedEpisodeEntity)

  @Query("DELETE FROM queued_episodes WHERE feedUrl = :feedUrl AND episodeId = :episodeId")
  suspend fun delete(feedUrl: String, episodeId: String)
}
