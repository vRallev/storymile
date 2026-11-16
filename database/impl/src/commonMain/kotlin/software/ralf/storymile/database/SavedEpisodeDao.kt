package software.ralf.storymile.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedEpisodeDao {
  @Query(
    """
    SELECT episodes.*, shows.feedUrl, shows.title AS podcastTitle, shows.artworkUrl AS showArtworkUrl
    FROM saved_episodes
    JOIN episodes ON episodes.id = saved_episodes.episodeId
    JOIN shows ON shows.id = episodes.showId
    ORDER BY saved_episodes.id DESC
    """,
  )
  fun observe(): Flow<List<StoredEpisode>>

  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(episode: SavedEpisodeEntity)

  @Query(
    """
    SELECT saved_episodes.id
    FROM saved_episodes
    JOIN episodes ON episodes.id = saved_episodes.episodeId
    WHERE episodes.showId = :showId AND episodes.sourceEpisodeId = :sourceEpisodeId
    """,
  )
  suspend fun findId(showId: Long, sourceEpisodeId: String): Long?

  @Query("DELETE FROM saved_episodes WHERE id = :id") suspend fun delete(id: Long)
}
