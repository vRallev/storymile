package software.ralf.storymile.database

import kotlinx.coroutines.flow.Flow
import software.ralf.storymile.podcast.PodcastFeed

/**
 * One user's saved podcasts. Real mode persists data; fake mode keeps it in memory. Access ends
 * when the user scope exits. Persistent data survives scope recreation. Reopening a persistent
 * database waits for its previous owner to close it. Active scopes cannot share its path.
 */
interface PodcastDatabase {
  /** Saved show metadata and episodes, ordered by feed URL. Emits an empty list when empty. */
  val podcasts: Flow<List<PodcastFeed>>

  /**
   * Inserts a podcast or replaces its metadata and episodes atomically, keyed by feed URL. Episode
   * order is preserved. If episode IDs repeat, the first occurrence is stored.
   */
  suspend fun insert(podcast: PodcastFeed)

  /** Removes the show and its episodes. Does nothing if [feedUrl] is not saved. */
  suspend fun delete(feedUrl: String)
}
