package software.ralf.storymile.design

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import software.ralf.storymile.theme.AppTheme

/**
 * Displays a podcast's artwork, title, and optional author. Apply the modifier passed to [artwork]
 * to its root. Use [showArrow] to indicate navigation. When [onClick] is null, the row is passive.
 */
@Composable
fun PodcastRow(
  title: String,
  author: String?,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  selected: Boolean = false,
  showArrow: Boolean = false,
  titleModifier: Modifier = Modifier,
  authorModifier: Modifier = Modifier,
  artwork: @Composable (Modifier) -> Unit,
) {
  val content: @Composable () -> Unit = {
    Row(
      Modifier.padding(8.dp),
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
        if (author != null) {
          Text(
            text = author,
            modifier = authorModifier,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (showArrow) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = AppTheme.colorScheme.primary,
        )
      }
    }
  }
  val rowModifier = modifier.fillMaxWidth()
  val color = if (selected) AppTheme.colorScheme.secondaryContainer else Color.Transparent
  val shape = RoundedCornerShape(16.dp)
  if (onClick == null) {
    Surface(modifier = rowModifier, shape = shape, color = color, content = content)
  } else {
    Surface(
      onClick = onClick,
      modifier = rowModifier,
      shape = shape,
      color = color,
      content = content,
    )
  }
}
