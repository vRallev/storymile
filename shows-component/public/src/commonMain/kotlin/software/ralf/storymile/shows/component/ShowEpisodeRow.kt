package software.ralf.storymile.shows.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeSharedElement
import software.ralf.storymile.episodedetails.episodeSharedElement
import software.ralf.storymile.shows.component.generated.resources.Res
import software.ralf.storymile.shows.component.generated.resources.play_episode
import software.ralf.storymile.theme.AppTheme

/**
 * Opens an episode preview through [onClick]. Use [edgeToEdge] to extend the touch target to the
 * full width while keeping the content inset. [tagPrefix] and [tagId] identify the row and its
 * elements within a screen.
 */
@Composable
fun ShowEpisodeRow(
  details: EpisodeDetails,
  onClick: () -> Unit,
  tagId: String,
  edgeToEdge: Boolean = false,
  tagPrefix: String = "episode",
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(if (edgeToEdge) RectangleShape else RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .testTag("$tagPrefix-$tagId")
        .padding(
          horizontal = if (edgeToEdge) 16.dp else 8.dp,
          vertical = if (edgeToEdge) 4.dp else 8.dp,
        ),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PodcastArtwork(
      artworkUrl = details.artworkUrl,
      modifier =
        Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Artwork)
          .testTag("$tagPrefix-artwork-$tagId")
          .size(56.dp),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = details.episode.title,
        modifier =
          Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Title)
            .testTag("$tagPrefix-title-$tagId"),
        style = AppTheme.typography.titleSmall.copy(textMotion = TextMotion.Animated),
      )
      Text(
        text = details.podcastTitle,
        modifier =
          Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Subtitle)
            .testTag("$tagPrefix-subtitle-$tagId"),
        style = AppTheme.typography.bodySmall.copy(textMotion = TextMotion.Animated),
        color = AppTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      details.duration?.let { duration ->
        Text(
          text = duration,
          modifier =
            Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Duration)
              .testTag("$tagPrefix-duration-$tagId"),
          style = AppTheme.typography.bodySmall.copy(textMotion = TextMotion.Animated),
          color = AppTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (details.description.isNotBlank()) {
        Text(
          text = details.description,
          modifier =
            Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Description)
              .testTag("$tagPrefix-description-$tagId"),
          style = AppTheme.typography.bodyMedium.copy(textMotion = TextMotion.Animated),
          color = AppTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    FilledIconButton(
      onClick = {},
      enabled = false,
      modifier =
        Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Play)
          .testTag("$tagPrefix-play-$tagId"),
    ) {
      Icon(Icons.Filled.PlayArrow, stringResource(Res.string.play_episode, details.episode.title))
    }
  }
}
