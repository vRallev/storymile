package software.ralf.storymile.database

import kotlinx.coroutines.flow.Flow
import software.ralf.storymile.podcast.QueuedEpisode

/** Stores the queue in the current user's podcast database. */
interface EpisodeQueueDatabase {
  /** Episodes in queue order with their saved metadata. Emits an empty list when empty. */
  val queuedEpisodes: Flow<List<QueuedEpisode>>

  /**
   * Enqueues an episode if it is not already queued. Uses [preferredPosition] if it is free and
   * appends otherwise.
   */
  suspend fun enqueue(episode: QueuedEpisode, preferredPosition: Long? = null)

  /** Removes the episode and returns its sort position, or null if the episode was absent. */
  suspend fun removeFromQueue(feedUrl: String, episodeId: String): Long?

  /**
   * Moves the entry with [feedUrl] and [episodeId] to the index of the entry with [targetFeedUrl]
   * and [targetEpisodeId]. Does nothing if either entry is missing.
   */
  suspend fun moveInQueue(
    feedUrl: String,
    episodeId: String,
    targetFeedUrl: String,
    targetEpisodeId: String,
  )
}
