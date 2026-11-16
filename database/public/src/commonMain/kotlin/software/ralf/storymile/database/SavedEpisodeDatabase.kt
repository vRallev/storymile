package software.ralf.storymile.database

import kotlinx.coroutines.flow.Flow
import software.ralf.storymile.podcast.SavedEpisode

/** Stores saved episodes in the current user's podcast database. */
interface SavedEpisodeDatabase {
  /** Saved episodes with their metadata, most recently saved first. */
  val savedEpisodes: Flow<List<SavedEpisode>>

  /** Saves [episode] if absent. Pass its [previousId] from removal to restore its order. */
  suspend fun save(episode: SavedEpisode, previousId: Long? = null)

  /** Removes the saved entry and returns its ID, or null if absent. */
  suspend fun removeSavedEpisode(feedUrl: String, episodeId: String): Long?
}
