package software.ralf.storymile.podcastshows

import software.ralf.storymile.discovery.DiscoveryTopic

/** Identifies the originating screen for show titles and shared element transitions. */
sealed interface ShowSource {
  /** The followed shows in Library. */
  data object Library : ShowSource

  /** A podcast catalog with its own heading and transition identity. */
  sealed interface Discovery : ShowSource

  /** The main Discovery catalog. */
  data object Browse : Discovery

  /** Results of a typed search. */
  data object Search : Discovery

  /** Results for one discovery topic. */
  data class Topic(
    /** The selected discovery topic. */
    val topic: DiscoveryTopic,
  ) : Discovery
}
