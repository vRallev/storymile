package software.ralf.storymile.podcast

import kotlinx.coroutines.flow.StateFlow

/**
 * Stores the current user's saved episodes. Writes return immediately and run in submission order
 * while the user scope is active. Observe [episodes] for saved changes. Failed writes are logged.
 */
interface SavedEpisodeRepository {
  /**
   * Saved episodes, most recently saved first. The first collector starts loading. The value is
   * null until a read succeeds and empty when there are no saved episodes. Failed reads keep the
   * last value and retry.
   */
  val episodes: StateFlow<List<SavedEpisode>?>

  /** Saves [episode] if its feed URL and episode ID are not already saved. */
  fun save(episode: SavedEpisode)

  /** Removes [episode] if its feed URL and episode ID are saved. */
  fun remove(episode: SavedEpisode)

  /**
   * Removes [episode] if saved. After a successful removal, calls [onRemoved] on the writer's
   * coroutine with an action that restores the episode to its previous place in the saved list.
   * Undo does nothing if the episode is already saved again.
   */
  fun removeWithUndo(episode: SavedEpisode, onRemoved: (undo: () -> Unit) -> Unit)
}
