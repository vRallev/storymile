package software.ralf.storymile.podcastshows

import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.podcast.PodcastFeed

sealed interface ShowListPane {
  data class Discovery(
    val podcasts: List<DiscoveredPodcast>,
    val onSelect: (DiscoveredPodcast) -> Unit,
  ) : ShowListPane

  data class Saved(val shows: List<PodcastFeed>, val onSelect: (PodcastFeed) -> Unit) : ShowListPane
}
