package software.ralf.storymile.podcast

import kotlinx.coroutines.flow.StateFlow

/** Shares podcast feeds in memory for the app session. */
interface PodcastFeedRepository {
  /**
   * Returns shared state for [feedUrl], ignoring surrounding whitespace. First collection loads the
   * feed. It refreshes 60 minutes after each completed fetch, even without collectors, until the
   * app session ends. An empty episode list is a successful result.
   */
  fun feed(feedUrl: String): StateFlow<State>

  /**
   * Refreshes one feed and restarts its interval, replacing an active fetch. Before first
   * collection, defers loading until collection starts.
   */
  fun refresh(feedUrl: String)

  /** The latest fetch state for one feed. */
  sealed interface State {
    /** The initial state or a fetch in progress. */
    data object Loading : State

    /** The last fetch failed. Call [refresh] to retry. */
    data object Error : State

    /** The latest feed snapshot, including episodes in feed order. */
    data class Success(
      /** Show details and episodes returned by the service. */
      val feed: PodcastFeed,
    ) : State
  }
}
