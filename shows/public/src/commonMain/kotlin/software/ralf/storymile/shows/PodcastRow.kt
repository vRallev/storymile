package software.ralf.storymile.shows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.shows.PodcastSharedElement.Companion.podcastSharedElement
import software.ralf.storymile.theme.AppTheme

/** Displays a selectable show row. A null transition source disables shared elements. */
@Composable
fun PodcastRow(
  podcast: DiscoveredPodcast,
  onClick: () -> Unit,
  selected: Boolean = false,
  transitionSource: String? = null,
  modifier: Modifier = Modifier,
  subtitle: String? = podcast.author,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().testTag("discovery-podcast-${podcast.id}"),
    color = if (selected) AppTheme.colorScheme.secondaryContainer else Color.Transparent,
    shape = RoundedCornerShape(16.dp),
  ) {
    Row(
      Modifier.padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PodcastArtwork(
        podcast,
        Modifier.podcastSharedElement(podcast, transitionSource, PodcastSharedElement.Artwork)
          .testTag("discovery-artwork-${podcast.id}")
          .size(76.dp),
      )
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = podcast.title,
          modifier =
            Modifier.podcastSharedElement(podcast, transitionSource, PodcastSharedElement.Title)
              .testTag("discovery-title-${podcast.id}"),
          style = AppTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let { author ->
          Text(
            text = author,
            modifier =
              Modifier.podcastSharedElement(podcast, transitionSource, PodcastSharedElement.Author)
                .testTag("discovery-author-${podcast.id}"),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (trailingContent != null) {
        trailingContent()
      } else {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = AppTheme.colorScheme.primary,
        )
      }
    }
  }
}
