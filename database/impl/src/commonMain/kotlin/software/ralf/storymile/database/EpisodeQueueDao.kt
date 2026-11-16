package software.ralf.storymile.database

import androidx.room3.Dao
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeQueueDao {
  @Query(
    """
    SELECT episodes.*, shows.feedUrl, shows.title AS podcastTitle, shows.artworkUrl AS showArtworkUrl
    FROM queued_episodes
    JOIN episodes ON episodes.id = queued_episodes.episodeId
    JOIN shows ON shows.id = episodes.showId
    ORDER BY queued_episodes.position, queued_episodes.id
    """,
  )
  fun observe(): Flow<List<StoredQueuedEpisode>>

  @Query(
    """
    INSERT OR IGNORE INTO queued_episodes (episodeId, position)
    SELECT :episodeId, COALESCE(MAX(position), 0) + 1 FROM queued_episodes
    """,
  )
  suspend fun append(episodeId: Long)

  @Query(
    """
    DELETE FROM queued_episodes
    WHERE episodeId IN (SELECT id FROM episodes WHERE showId = :showId AND sourceEpisodeId = :sourceEpisodeId)
    """,
  )
  suspend fun delete(showId: Long, sourceEpisodeId: String): Int
}
