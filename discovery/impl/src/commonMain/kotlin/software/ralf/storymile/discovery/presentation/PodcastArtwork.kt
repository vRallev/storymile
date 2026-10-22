package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.discovery.DiscoveredPodcast

@Composable
internal fun PodcastArtwork(podcast: DiscoveredPodcast, modifier: Modifier) {
  PodcastArtwork(artworkUrl = podcast.artworkUrl, modifier = modifier, artworkId = podcast.id)
}
