package software.ralf.storymile.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import software.ralf.storymile.theme.AppTheme

/**
 * Displays an episode with its artwork and trailing actions. Pass null for [selected] when the row
 * opens a separate destination. Apply the supplied size modifier to [artwork].
 */
@Composable
fun LibraryEpisodeRow(
  title: String,
  podcastTitle: String,
  duration: String?,
  compact: Boolean,
  selected: Boolean?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  titleModifier: Modifier = Modifier,
  subtitleModifier: Modifier = Modifier,
  durationModifier: @Composable () -> Modifier = { Modifier },
  artwork: @Composable (Modifier) -> Unit,
  actions: @Composable (Color) -> Unit,
) {
  val titleStyle = if (compact) AppTheme.typography.titleSmall else AppTheme.typography.titleMedium
  val metadataStyle = if (compact) AppTheme.typography.bodySmall else AppTheme.typography.bodyMedium
  val metadataColor =
    if (selected == true) {
      AppTheme.colorScheme.onSecondaryContainer
    } else {
      AppTheme.colorScheme.onSurfaceVariant
    }
  Surface(
    onClick = onClick,
    modifier =
      (if (selected == null) modifier else modifier.semantics { this.selected = selected })
        .fillMaxWidth(),
    shape = AppTheme.shapes.medium,
    color = if (selected == true) AppTheme.colorScheme.secondaryContainer else Color.Transparent,
  ) {
    Row(
      modifier = Modifier.padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      artwork(Modifier.size(if (compact) 64.dp else 88.dp))
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = title,
          modifier = titleModifier,
          style = titleStyle.copy(textMotion = TextMotion.Animated),
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = podcastTitle,
          modifier = subtitleModifier,
          style = metadataStyle.copy(textMotion = TextMotion.Animated),
          color = metadataColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (duration != null) {
          Text(
            text = duration,
            modifier = durationModifier(),
            style = metadataStyle.copy(textMotion = TextMotion.Animated),
            color = metadataColor,
          )
        }
      }
      actions(metadataColor)
    }
  }
}
