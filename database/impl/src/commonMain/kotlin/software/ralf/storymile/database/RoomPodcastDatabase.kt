package software.ralf.storymile.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.room3.withWriteTransaction
import androidx.sqlite.execSQL
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.podcast.SavedEpisode

@Database(
  entities =
    [
      ShowEntity::class,
      EpisodeEntity::class,
      QueuedEpisodeEntity::class,
      SavedEpisodeEntity::class,
    ],
  version = 2,
)
@ConstructedBy(RoomPodcastDatabaseConstructor::class)
abstract class RoomPodcastDatabase : RoomDatabase() {
  abstract fun podcasts(): PodcastDao

  abstract fun episodes(): EpisodeDao

  abstract fun queue(): EpisodeQueueDao

  abstract fun savedEpisodes(): SavedEpisodeDao

  internal suspend fun follow(podcast: PodcastFeed) = withWriteTransaction {
    val shows = podcasts()
    val stored = shows.findShow(podcast.feedUrl)
    val show =
      ShowEntity(
        id = stored?.id ?: 0,
        feedUrl = podcast.feedUrl,
        title = podcast.title,
        description = podcast.description,
        author = podcast.author,
        artworkUrl = podcast.artworkUrl,
        link = podcast.link,
        isFollowed = true,
      )
    val showId =
      when {
        stored == null -> shows.insertShow(show)
        stored == show -> stored.id
        else -> stored.id.also { shows.updateShow(show) }
      }
    refreshEpisodes(showId = showId, feedEpisodes = podcast.episodes.distinctBy { it.id })
  }

  internal suspend fun unfollow(feedUrl: String) = withWriteTransaction {
    val shows = podcasts()
    val showId = shows.findShowId(feedUrl) ?: return@withWriteTransaction
    shows.clearFollow(showId)
    episodes().clearFeedPositions(showId)
    pruneUnretained(showId)
  }

  internal suspend fun enqueue(queued: QueuedEpisode, position: Long?) = withWriteTransaction {
    val episodeId =
      ensureEpisode(
        feedUrl = queued.feedUrl,
        podcastTitle = queued.podcastTitle,
        artworkUrl = queued.artworkUrl,
        episode = queued.episode,
      )
    queue().insert(episodeId = episodeId, preferredPosition = position)
  }

  internal suspend fun removeFromQueue(feedUrl: String, episodeId: String): Long? =
    withWriteTransaction {
      val showId = podcasts().findShowId(feedUrl) ?: return@withWriteTransaction null
      val queue = queue()
      val position =
        queue.findPosition(feedUrl = feedUrl, sourceEpisodeId = episodeId)
          ?: return@withWriteTransaction null
      queue.delete(showId = showId, sourceEpisodeId = episodeId)
      pruneUnretained(showId)
      position
    }

  internal suspend fun moveInQueue(
    feedUrl: String,
    episodeId: String,
    targetFeedUrl: String,
    targetEpisodeId: String,
  ) = withWriteTransaction {
    val queue = queue()
    val source =
      queue.findPosition(feedUrl = feedUrl, sourceEpisodeId = episodeId)
        ?: return@withWriteTransaction
    val target =
      queue.findPosition(feedUrl = targetFeedUrl, sourceEpisodeId = targetEpisodeId)
        ?: return@withWriteTransaction
    if (source != target) {
      queue.move(sourcePosition = source, targetPosition = target)
    }
  }

  internal suspend fun save(saved: SavedEpisode, previousId: Long?) = withWriteTransaction {
    val episodeId =
      ensureEpisode(
        feedUrl = saved.feedUrl,
        podcastTitle = saved.podcastTitle,
        artworkUrl = saved.artworkUrl,
        episode = saved.episode,
      )
    savedEpisodes().insert(SavedEpisodeEntity(id = previousId ?: 0, episodeId = episodeId))
  }

  internal suspend fun removeSavedEpisode(feedUrl: String, episodeId: String): Long? =
    withWriteTransaction {
      val showId = podcasts().findShowId(feedUrl) ?: return@withWriteTransaction null
      val saved = savedEpisodes()
      val id =
        saved.findId(showId = showId, sourceEpisodeId = episodeId)
          ?: return@withWriteTransaction null
      saved.delete(id)
      pruneUnretained(showId)
      id
    }

  private suspend fun ensureEpisode(
    feedUrl: String,
    podcastTitle: String,
    artworkUrl: String?,
    episode: PodcastEpisode,
  ): Long {
    val showId =
      ensureShow(
        feedUrl = feedUrl,
        podcastTitle = podcastTitle,
        artworkUrl = artworkUrl.takeIf { episode.artworkUrl == null },
      )
    val episodes = episodes()
    return episodes.findEpisodeId(showId = showId, sourceEpisodeId = episode.id)
      ?: episodes.insertEpisode(episode.toEntity(showId = showId, feedPosition = null))
  }

  private suspend fun ensureShow(feedUrl: String, podcastTitle: String, artworkUrl: String?): Long {
    val shows = podcasts()
    val show = shows.findShow(feedUrl)
    if (show == null) {
      return shows.insertShow(
        ShowEntity(
          feedUrl = feedUrl,
          title = podcastTitle,
          description = null,
          author = null,
          artworkUrl = artworkUrl,
          link = null,
        ),
      )
    }
    if (!show.isFollowed && show.artworkUrl == null && artworkUrl != null) {
      shows.updateShow(show.copy(artworkUrl = artworkUrl))
    }
    return show.id
  }

  private suspend fun refreshEpisodes(showId: Long, feedEpisodes: List<PodcastEpisode>) {
    val episodes = episodes()
    val stored = episodes.findEpisodes(showId)
    val storedBySourceId = stored.associateBy { it.sourceEpisodeId }
    val storedByMediaUrl = stored.groupBy { it.enclosure.url }
    val incomingSourceIds = feedEpisodes.mapTo(mutableSetOf()) { it.id }
    val incomingMediaCounts = feedEpisodes.groupingBy { it.enclosure.url }.eachCount()
    val missing =
      stored.mapNotNullTo(mutableSetOf()) { if (it.feedPosition != null) it.id else null }
    val added = mutableListOf<EpisodeEntity>()
    val updated = mutableListOf<EpisodeEntity>()
    feedEpisodes.forEachIndexed { position, episode ->
      val mediaUrl = episode.enclosure.url
      val match =
        storedBySourceId[episode.id]
          ?: storedByMediaUrl[mediaUrl]?.singleOrNull()?.takeIf {
            it.sourceEpisodeId !in incomingSourceIds && incomingMediaCounts[mediaUrl] == 1
          }
      val row = episode.toEntity(id = match?.id ?: 0, showId = showId, feedPosition = position)
      if (match == null) {
        added += row
      } else {
        missing.remove(match.id)
        if (row != match) {
          updated += row
        }
      }
    }

    if (updated.isNotEmpty()) {
      episodes.updateEpisodes(updated)
    }
    if (added.isNotEmpty()) {
      episodes.insertEpisodes(added)
    }
    if (missing.isNotEmpty()) {
      // Stay within SQLite's bind parameter limit on every platform.
      missing.chunked(999).forEach { episodes.clearFeedPositions(it) }
      episodes.deleteUnretainedEpisodes(showId)
    }
  }

  private suspend fun pruneUnretained(showId: Long) {
    episodes().deleteUnretainedEpisodes(showId)
    podcasts().deleteEmptyUnfollowedShow(showId)
  }

  private fun PodcastEpisode.toEntity(
    id: Long = 0,
    showId: Long,
    feedPosition: Int?,
  ): EpisodeEntity =
    EpisodeEntity(
      id = id,
      showId = showId,
      sourceEpisodeId = this.id,
      feedPosition = feedPosition,
      title = title,
      description = description,
      author = author,
      artworkUrl = artworkUrl,
      publishedAt = publishedAt,
      durationMillis = duration?.inWholeMilliseconds,
      enclosure = enclosure,
    )

  companion object {
    internal val migration1To2 =
      Migration(startVersion = 1, endVersion = 2) { connection ->
        connection.execSQL(
          "CREATE INDEX index_episodes_feedPosition_showId ON episodes (feedPosition, showId)",
        )
      }
  }
}
