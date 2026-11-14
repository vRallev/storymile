package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import software.ralf.storymile.design.PodcastRow
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.podcastshows.PodcastSharedElement
import software.ralf.storymile.podcastshows.PodcastSharedElement.Companion.podcastSharedElement
import software.ralf.storymile.podcastshows.ShowSource

@Composable
internal fun DiscoveryPodcastRow(
  podcast: DiscoveredPodcast,
  onClick: () -> Unit,
  transitionSource: ShowSource.Discovery? = null,
) {
  PodcastRow(
    title = podcast.title,
    author = podcast.author,
    modifier = Modifier.testTag("discovery-podcast-${podcast.id}"),
    onClick = onClick,
    showArrow = true,
    titleModifier =
      Modifier.podcastSharedElement(
          podcastId = podcast.id,
          source = transitionSource,
          element = PodcastSharedElement.Title,
        )
        .testTag("discovery-title-${podcast.id}"),
    authorModifier =
      Modifier.podcastSharedElement(
          podcastId = podcast.id,
          source = transitionSource,
          element = PodcastSharedElement.Author,
        )
        .testTag("discovery-author-${podcast.id}"),
  ) { artworkModifier ->
    PodcastArtwork(
      podcast = podcast,
      modifier =
        Modifier.podcastSharedElement(
            podcastId = podcast.id,
            source = transitionSource,
            element = PodcastSharedElement.Artwork,
          )
          .testTag("discovery-artwork-${podcast.id}")
          .then(artworkModifier),
    )
  }
}
