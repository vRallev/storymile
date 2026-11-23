package software.ralf.storymile.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface EpisodeDao {
  @Query("SELECT * FROM episodes WHERE showId = :showId")
  suspend fun findEpisodes(showId: Long): List<EpisodeEntity>

  @Query("SELECT id FROM episodes WHERE showId = :showId AND sourceEpisodeId = :sourceEpisodeId")
  suspend fun findEpisodeId(showId: Long, sourceEpisodeId: String): Long?

  @Insert suspend fun insertEpisode(episode: EpisodeEntity): Long

  @Insert suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

  @Update suspend fun updateEpisodes(episodes: List<EpisodeEntity>)

  @Query(
    "UPDATE episodes SET feedPosition = NULL WHERE showId = :showId AND feedPosition IS NOT NULL",
  )
  suspend fun clearFeedPositions(showId: Long)

  @Query("UPDATE episodes SET feedPosition = NULL WHERE id IN (:ids)")
  suspend fun clearFeedPositions(ids: List<Long>)

  @Query(
    """
    DELETE FROM episodes
    WHERE showId = :showId AND feedPosition IS NULL
      AND NOT EXISTS (SELECT 1 FROM queued_episodes WHERE episodeId = episodes.id)
      AND NOT EXISTS (SELECT 1 FROM saved_episodes WHERE episodeId = episodes.id)
    """,
  )
  suspend fun deleteUnretainedEpisodes(showId: Long)
}
