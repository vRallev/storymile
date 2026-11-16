package software.ralf.storymile.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.QueuedEpisode

@Dao
interface PodcastDao {
  @Transaction
  @Query("SELECT * FROM shows WHERE isFollowed ORDER BY feedUrl")
  fun observe(): Flow<List<StoredPodcast>>

  @Transaction
  suspend fun insert(podcast: PodcastFeed) {
    val stored = findShowId(podcast.feedUrl)
    val show =
      ShowEntity(
        id = stored ?: 0,
        feedUrl = podcast.feedUrl,
        title = podcast.title,
        description = podcast.description,
        author = podcast.author,
        artworkUrl = podcast.artworkUrl,
        link = podcast.link,
        isFollowed = true,
      )
    val showId =
      if (stored == null) {
        insertShow(show)
      } else {
        updateShow(show)
        stored
      }
    refreshEpisodes(showId = showId, episodes = podcast.episodes.distinctBy { it.id })
  }

  @Transaction
  suspend fun delete(feedUrl: String) {
    val showId = findShowId(feedUrl) ?: return
    clearFollow(showId)
    clearFeedPositions(showId)
    pruneUnretained(showId)
  }

  suspend fun ensureEpisode(queued: QueuedEpisode): Long {
    val showArtwork = queued.artworkUrl.takeIf { queued.episode.artworkUrl == null }
    val show = findShow(queued.feedUrl)
    val showId =
      if (show == null) {
        insertShow(
          ShowEntity(
            feedUrl = queued.feedUrl,
            title = queued.podcastTitle,
            description = null,
            author = null,
            artworkUrl = showArtwork,
            link = null,
          ),
        )
      } else {
        if (!show.isFollowed && show.artworkUrl == null && showArtwork != null) {
          updateShow(show.copy(artworkUrl = showArtwork))
        }
        show.id
      }
    return findEpisodeId(showId = showId, sourceEpisodeId = queued.episode.id)
      ?: insertEpisode(queued.episode.toEntity(showId = showId, feedPosition = null))
  }

  suspend fun pruneUnretained(showId: Long) {
    deleteUnretainedEpisodes(showId)
    deleteEmptyUnfollowedShow(showId)
  }

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

  @Query(
    """
    DELETE FROM episodes
    WHERE showId = :showId AND feedPosition IS NULL
      AND NOT EXISTS (SELECT 1 FROM queued_episodes WHERE episodeId = episodes.id)
    """,
  )
  suspend fun deleteUnretainedEpisodes(showId: Long)

  private suspend fun refreshEpisodes(showId: Long, episodes: List<PodcastEpisode>) {
    val stored = findEpisodes(showId)
    val storedBySourceId = stored.associateBy { it.sourceEpisodeId }
    val storedByMediaUrl = stored.groupBy { it.enclosure.url }
    val incomingSourceIds = episodes.map { it.id }.toSet()
    val incomingMediaCounts = episodes.groupingBy { it.enclosure.url }.eachCount()
    val refreshed = episodes.mapIndexed { position, episode ->
      val mediaUrl = episode.enclosure.url
      val match =
        storedBySourceId[episode.id]
          ?: storedByMediaUrl[mediaUrl]?.singleOrNull()?.takeIf {
            // An exact source ID match takes precedence over a media URL match.
            it.sourceEpisodeId !in incomingSourceIds && incomingMediaCounts[mediaUrl] == 1
          }
      episode.toEntity(showId = showId, feedPosition = position, localId = match?.id ?: 0)
    }

    clearFeedPositions(showId)
    val (updated, added) = refreshed.partition { it.id != 0L }
    updateEpisodes(updated)
    insertEpisodes(added)
    deleteUnretainedEpisodes(showId)
  }

  private fun PodcastEpisode.toEntity(
    showId: Long,
    feedPosition: Int?,
    localId: Long = 0,
  ): EpisodeEntity =
    EpisodeEntity(
      id = localId,
      showId = showId,
      sourceEpisodeId = id,
      feedPosition = feedPosition,
      title = title,
      description = description,
      author = author,
      artworkUrl = artworkUrl,
      publishedAt = publishedAt,
      durationMillis = duration?.inWholeMilliseconds,
      enclosure = enclosure,
    )
}
