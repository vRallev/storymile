package software.ralf.storymile.shows.component

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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import software.ralf.storymile.theme.AppTheme

/**
 * Shows a tappable podcast summary. Apply the modifier passed to [artwork] to reserve the artwork
 * space. [trailingContent] defaults to a chevron; replace it to add row actions.
 */
@Composable
fun ShowRow(
  title: String,
  subtitle: String?,
  onClick: () -> Unit,
  artwork: @Composable (Modifier) -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  titleModifier: Modifier = Modifier,
  subtitleModifier: Modifier = Modifier,
  supportingContent: @Composable (() -> Unit)? = null,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  Surface(
    onClick = onClick,
    modifier =
      modifier
        .fillMaxWidth()
        .then(
          if (selected) Modifier.semantics { this.selected = true } else Modifier,
        ),
    color = if (selected) AppTheme.colorScheme.secondaryContainer else Color.Transparent,
    shape = RoundedCornerShape(16.dp),
  ) {
    Row(
      modifier = Modifier.padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      artwork(Modifier.size(76.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = title,
          modifier = titleModifier,
          style = AppTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
          Text(
            text = subtitle,
            modifier = subtitleModifier,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        supportingContent?.invoke()
      }
      if (trailingContent == null) {
        ShowChevron()
      } else {
        trailingContent()
      }
    }
  }
}

@Composable
private fun ShowChevron() {
  Icon(
    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    contentDescription = null,
    tint = AppTheme.colorScheme.primary,
  )
}
