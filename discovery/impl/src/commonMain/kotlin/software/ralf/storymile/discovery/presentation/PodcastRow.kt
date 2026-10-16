package software.ralf.storymile.discovery.presentation

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
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun PodcastRow(
  podcast: DiscoveredPodcast,
  onClick: () -> Unit,
  selected: Boolean = false,
  transitionSource: ShowPresenter.Source? = null,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().testTag("discovery-podcast-${podcast.id}"),
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
        PodcastSharedElement.Artwork.modifier(podcast, transitionSource)
          .testTag("discovery-artwork-${podcast.id}")
          .size(76.dp),
      )
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = podcast.title,
          modifier =
            PodcastSharedElement.Title.modifier(podcast, transitionSource)
              .testTag("discovery-title-${podcast.id}"),
          style = AppTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        podcast.author?.let { author ->
          Text(
            text = author,
            modifier =
              PodcastSharedElement.Author.modifier(podcast, transitionSource)
                .testTag("discovery-author-${podcast.id}"),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = AppTheme.colorScheme.primary,
      )
    }
  }
}
