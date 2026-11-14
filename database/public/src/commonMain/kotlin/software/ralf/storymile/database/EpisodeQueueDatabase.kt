package software.ralf.storymile.database

import kotlinx.coroutines.flow.Flow
import software.ralf.storymile.podcast.QueuedEpisode

/** Stores the queue in the current user's podcast database. */
interface EpisodeQueueDatabase {
  /** Episodes in queue order. Emits an empty list when the queue is empty. */
  val queuedEpisodes: Flow<List<QueuedEpisode>>

  /** Appends an episode if its feed URL and episode ID are not already in the queue. */
  suspend fun enqueue(episode: QueuedEpisode)

  /** Removes the episode identified by [feedUrl] and [episodeId], if present. */
  suspend fun removeFromQueue(feedUrl: String, episodeId: String)
}
