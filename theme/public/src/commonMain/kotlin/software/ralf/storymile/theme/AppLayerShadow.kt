package software.ralf.storymile.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp

/** Draws a soft shadow around an app layer without changing its bounds or clipping its content. */
@Composable
fun Modifier.appLayerShadow(shape: Shape): Modifier {
  val colors = AppTheme.colorScheme
  val alpha = if (colors.surface.luminance() > 0.5f) 0.12f else 0.24f
  return dropShadow(shape, Shadow(radius = 8.dp, color = colors.scrim, alpha = alpha))
}
