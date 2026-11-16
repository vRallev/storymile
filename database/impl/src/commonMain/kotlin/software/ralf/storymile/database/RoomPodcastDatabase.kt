package software.ralf.storymile.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.QueuedEpisode

@Database(
  entities = [ShowEntity::class, EpisodeEntity::class, QueuedEpisodeEntity::class],
  version = 1,
)
@ConstructedBy(RoomPodcastDatabaseConstructor::class)
abstract class RoomPodcastDatabase : RoomDatabase() {
  abstract fun podcasts(): PodcastDao

  abstract fun episodes(): EpisodeDao

  abstract fun queue(): EpisodeQueueDao

  internal suspend fun follow(podcast: PodcastFeed) = withWriteTransaction {
    val shows = podcasts()
    val storedId = shows.findShowId(podcast.feedUrl)
    val show =
      ShowEntity(
        id = storedId ?: 0,
        feedUrl = podcast.feedUrl,
        title = podcast.title,
        description = podcast.description,
        author = podcast.author,
        artworkUrl = podcast.artworkUrl,
        link = podcast.link,
        isFollowed = true,
      )
    val showId =
      if (storedId == null) shows.insertShow(show) else storedId.also { shows.updateShow(show) }
    refreshEpisodes(showId = showId, feedEpisodes = podcast.episodes.distinctBy { it.id })
  }

  internal suspend fun unfollow(feedUrl: String) = withWriteTransaction {
    val shows = podcasts()
    val showId = shows.findShowId(feedUrl) ?: return@withWriteTransaction
    shows.clearFollow(showId)
    episodes().clearFeedPositions(showId)
    pruneUnretained(showId)
  }

  internal suspend fun enqueue(queued: QueuedEpisode) = withWriteTransaction {
    val showId = ensureShowForQueue(queued)
    val episodes = episodes()
    val episodeId =
      episodes.findEpisodeId(showId = showId, sourceEpisodeId = queued.episode.id)
        ?: episodes.insertEpisode(queued.episode.toEntity(showId = showId, feedPosition = null))
    queue().append(episodeId)
  }

  internal suspend fun removeFromQueue(feedUrl: String, episodeId: String) = withWriteTransaction {
    val showId = podcasts().findShowId(feedUrl) ?: return@withWriteTransaction
    if (queue().delete(showId = showId, sourceEpisodeId = episodeId) > 0) {
      pruneUnretained(showId)
    }
  }

  private suspend fun ensureShowForQueue(queued: QueuedEpisode): Long {
    val shows = podcasts()
    val showArtwork = queued.artworkUrl.takeIf { queued.episode.artworkUrl == null }
    val show = shows.findShow(queued.feedUrl)
    if (show == null) {
      return shows.insertShow(
        ShowEntity(
          feedUrl = queued.feedUrl,
          title = queued.podcastTitle,
          description = null,
          author = null,
          artworkUrl = showArtwork,
          link = null,
        ),
      )
    }
    if (!show.isFollowed && show.artworkUrl == null && showArtwork != null) {
      shows.updateShow(show.copy(artworkUrl = showArtwork))
    }
    return show.id
  }

  private suspend fun refreshEpisodes(showId: Long, feedEpisodes: List<PodcastEpisode>) {
    val episodes = episodes()
    val stored = episodes.findEpisodes(showId)
    val storedBySourceId = stored.associateBy { it.sourceEpisodeId }
    val storedByMediaUrl = stored.groupBy { it.enclosure.url }
    val incomingSourceIds = feedEpisodes.map { it.id }.toSet()
    val incomingMediaCounts = feedEpisodes.groupingBy { it.enclosure.url }.eachCount()
    val refreshed = feedEpisodes.mapIndexed { position, episode ->
      val mediaUrl = episode.enclosure.url
      val match =
        storedBySourceId[episode.id]
          ?: storedByMediaUrl[mediaUrl]?.singleOrNull()?.takeIf {
            it.sourceEpisodeId !in incomingSourceIds && incomingMediaCounts[mediaUrl] == 1
          }
      episode.toEntity(id = match?.id ?: 0, showId = showId, feedPosition = position)
    }

    episodes.clearFeedPositions(showId)
    val (updated, added) = refreshed.partition { it.id != 0L }
    episodes.updateEpisodes(updated)
    episodes.insertEpisodes(added)
    episodes.deleteUnretainedEpisodes(showId)
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
}
