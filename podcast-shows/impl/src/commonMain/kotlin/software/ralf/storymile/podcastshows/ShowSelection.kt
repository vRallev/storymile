package software.ralf.storymile.podcastshows

import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.podcast.PodcastFeed

sealed interface ShowSelection {
  data class Discovery(
    val initialPodcast: DiscoveredPodcast,
    val podcasts: List<DiscoveredPodcast>,
  ) : ShowSelection

  data class Saved(val initialShow: PodcastFeed) : ShowSelection
}
