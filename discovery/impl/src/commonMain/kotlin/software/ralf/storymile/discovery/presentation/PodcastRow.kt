package software.ralf.storymile.discovery.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.presentation.PodcastSharedElement.Companion.podcastSharedElement
import software.ralf.storymile.shows.component.ShowRow
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun PodcastRow(
  podcast: DiscoveredPodcast,
  onClick: () -> Unit,
  selected: Boolean = false,
  transitionSource: ShowPresenter.Source? = null,
) {
  ShowRow(
    title = podcast.title,
    subtitle = podcast.author,
    onClick = onClick,
    artwork = { modifier ->
      PodcastArtwork(
        podcast = podcast,
        modifier =
          Modifier.podcastSharedElement(podcast, transitionSource, PodcastSharedElement.Artwork)
            .testTag("discovery-artwork-${podcast.id}")
            .then(modifier),
      )
    },
    modifier = Modifier.testTag("discovery-podcast-${podcast.id}"),
    selected = selected,
    titleModifier =
      Modifier.podcastSharedElement(podcast, transitionSource, PodcastSharedElement.Title)
        .testTag("discovery-title-${podcast.id}"),
    subtitleModifier =
      Modifier.podcastSharedElement(podcast, transitionSource, PodcastSharedElement.Author)
        .testTag("discovery-author-${podcast.id}"),
    trailingContent = {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = AppTheme.colorScheme.primary,
      )
    },
  )
}
