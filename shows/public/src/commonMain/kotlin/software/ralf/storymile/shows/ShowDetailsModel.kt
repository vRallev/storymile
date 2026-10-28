package software.ralf.storymile.shows

import software.ralf.storymile.discovery.DiscoveredPodcast

/** State and actions for the shared show detail view. */
data class ShowDetailsModel(
  /** Show metadata. */
  val podcast: DiscoveredPodcast,
  /** Episodes in feed order. */
  val episodes: ShowEpisodes,
  /** Whether the summary is expanded. */
  val descriptionExpanded: Boolean,
  /** Whether the show is in the user's library. */
  val followed: Boolean,
  /** Whether the follow action is available. */
  val followEnabled: Boolean,
  /** Whether the last follow operation failed. */
  val followFailed: Boolean,
  /** Follows, unfollows, or retries a failed operation. */
  val onToggleFollow: () -> Unit,
  /** Expands or collapses the summary. */
  val onToggleDescription: () -> Unit,
)
