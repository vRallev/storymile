package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import software.ralf.storymile.design.PodcastRow
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.presentation.PodcastSharedElement.Companion.podcastSharedElement

@Composable
internal fun DiscoveryPodcastRow(
  podcast: DiscoveredPodcast,
  onClick: () -> Unit,
  selected: Boolean = false,
  transitionSource: ShowPresenter.Source? = null,
) {
  PodcastRow(
    title = podcast.title,
    author = podcast.author,
    modifier = Modifier.testTag("discovery-podcast-${podcast.id}"),
    onClick = onClick,
    selected = selected,
    showArrow = true,
    titleModifier =
      Modifier.podcastSharedElement(
          podcast = podcast,
          source = transitionSource,
          element = PodcastSharedElement.Title,
        )
        .testTag("discovery-title-${podcast.id}"),
    authorModifier =
      Modifier.podcastSharedElement(
          podcast = podcast,
          source = transitionSource,
          element = PodcastSharedElement.Author,
        )
        .testTag("discovery-author-${podcast.id}"),
  ) { artworkModifier ->
    PodcastArtwork(
      podcast = podcast,
      modifier =
        Modifier.podcastSharedElement(
            podcast = podcast,
            source = transitionSource,
            element = PodcastSharedElement.Artwork,
          )
          .testTag("discovery-artwork-${podcast.id}")
          .then(artworkModifier),
    )
  }
}
