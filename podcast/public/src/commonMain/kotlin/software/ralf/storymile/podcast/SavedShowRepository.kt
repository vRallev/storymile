package software.ralf.storymile.podcast

import kotlinx.coroutines.flow.StateFlow

/**
 * Stores the current user's followed shows and their episodes. Writes return immediately and run in
 * submission order while the user scope is active. Observe [shows] for saved changes. Failed writes
 * are logged.
 */
interface SavedShowRepository {
  /**
   * The first collector starts loading. The value is null until a read succeeds and empty when
   * there are no saved shows. A failed read leaves the value intact while the repository retries.
   */
  val shows: StateFlow<List<PodcastFeed>?>

  /** Saves [show] and its episodes together, replacing any saved show with the same feed URL. */
  fun save(show: PodcastFeed)

  /** Removes the show and its episodes. Does nothing if [feedUrl] is not saved. */
  fun remove(feedUrl: String)
}
