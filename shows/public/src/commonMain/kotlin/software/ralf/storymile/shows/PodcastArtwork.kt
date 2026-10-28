package software.ralf.storymile.shows

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.discovery.DiscoveredPodcast

/** Displays a show cover with the shared artwork fallback. */
@Composable
fun PodcastArtwork(podcast: DiscoveredPodcast, modifier: Modifier) {
  PodcastArtwork(artworkUrl = podcast.artworkUrl, modifier = modifier, artworkId = podcast.id)
}
