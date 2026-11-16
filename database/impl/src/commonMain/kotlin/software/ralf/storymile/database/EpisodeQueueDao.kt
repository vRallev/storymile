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
  fun observe(): Flow<List<StoredEpisode>>

  @Query(
    """
    INSERT OR IGNORE INTO queued_episodes (episodeId, position)
    VALUES (
      :episodeId,
      CASE
        WHEN :preferredPosition IS NOT NULL AND NOT EXISTS (
          SELECT 1 FROM queued_episodes WHERE position = :preferredPosition
        ) THEN :preferredPosition
        ELSE (SELECT COALESCE(MAX(position), 0) + 1 FROM queued_episodes)
      END
    )
    """,
  )
  suspend fun insert(episodeId: Long, preferredPosition: Long?)

  @Query(
    """
    DELETE FROM queued_episodes
    WHERE episodeId IN (SELECT id FROM episodes WHERE showId = :showId AND sourceEpisodeId = :sourceEpisodeId)
    """,
  )
  suspend fun delete(showId: Long, sourceEpisodeId: String)

  @Query(
    """
    SELECT queued_episodes.position
    FROM queued_episodes
    JOIN episodes ON episodes.id = queued_episodes.episodeId
    JOIN shows ON shows.id = episodes.showId
    WHERE shows.feedUrl = :feedUrl AND episodes.sourceEpisodeId = :sourceEpisodeId
    """,
  )
  suspend fun findPosition(feedUrl: String, sourceEpisodeId: String): Long?

  @Query(
    """
    UPDATE queued_episodes
    SET position = CASE
      WHEN position = :sourcePosition THEN :targetPosition
      WHEN :sourcePosition < :targetPosition THEN position - 1
      ELSE position + 1
    END
    WHERE position BETWEEN min(:sourcePosition, :targetPosition) AND max(:sourcePosition, :targetPosition)
    """,
  )
  suspend fun move(sourcePosition: Long, targetPosition: Long)
}
