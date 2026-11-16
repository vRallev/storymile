package software.ralf.storymile.database

import kotlinx.coroutines.flow.Flow
import software.ralf.storymile.podcast.SavedEpisode

/** Stores saved episodes in the current user's podcast database. */
interface SavedEpisodeDatabase {
  /** Saved episodes with their metadata, most recently saved first. */
  val savedEpisodes: Flow<List<SavedEpisode>>

  /** Saves [episode] if its feed URL and episode ID are not already saved. */
  suspend fun save(episode: SavedEpisode)

  /** Removes the saved entry identified by [feedUrl] and [episodeId], if present. */
  suspend fun removeSavedEpisode(feedUrl: String, episodeId: String)
}
