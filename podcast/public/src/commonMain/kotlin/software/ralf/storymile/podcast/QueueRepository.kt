package software.ralf.storymile.podcast

import kotlinx.coroutines.flow.StateFlow

/**
 * Stores the current user's episode queue. Writes return immediately and run in submission order
 * while the user scope is active. Observe [episodes] for saved changes. Failed writes are logged.
 */
interface QueueRepository {
  /**
   * Episodes in queue order. The first collector starts loading. The value is null until a read
   * succeeds and empty when the queue is empty. Failed reads keep the last value and retry.
   */
  val episodes: StateFlow<List<QueuedEpisode>?>

  /** Appends [episode]. If its feed URL and episode ID are already queued, does nothing. */
  fun save(episode: QueuedEpisode)

  /** Removes [episode] if its feed URL and episode ID are queued. */
  fun remove(episode: QueuedEpisode)

  /**
   * Moves [episode] to the current index of [target]. Matches entries by feed URL and episode ID.
   * Does nothing if either entry is missing.
   */
  fun move(episode: QueuedEpisode, target: QueuedEpisode)
}
