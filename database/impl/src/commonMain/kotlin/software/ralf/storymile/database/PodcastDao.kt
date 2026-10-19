package software.ralf.storymile.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import software.ralf.storymile.rss.PodcastFeed

@Dao
interface PodcastDao {
  @Transaction
  @Query("SELECT * FROM shows ORDER BY feedUrl")
  fun observe(): Flow<List<StoredPodcast>>

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertShow(show: ShowEntity)

  @Insert suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

  @Transaction
  suspend fun insert(podcast: PodcastFeed) {
    // Replacing the show cascades deletion of its previous episode snapshot.
    insertShow(
      ShowEntity(
        feedUrl = podcast.feedUrl,
        title = podcast.title,
        description = podcast.description,
        author = podcast.author,
        artworkUrl = podcast.artworkUrl,
        link = podcast.link,
      ),
    )
    insertEpisodes(
      podcast.episodes
        .distinctBy { it.id }
        .mapIndexed { position, episode ->
          EpisodeEntity(
            feedUrl = podcast.feedUrl,
            id = episode.id,
            position = position,
            title = episode.title,
            description = episode.description,
            author = episode.author,
            artworkUrl = episode.artworkUrl,
            publishedAt = episode.publishedAt,
            durationMillis = episode.duration?.inWholeMilliseconds,
            enclosure = episode.enclosure,
          )
        },
    )
  }
}
