package software.ralf.storymile.design

import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.storymile.theme.AppTheme

/** Separates side-by-side panes with a subtle line that fills the available height. */
@Composable
fun PaneDivider(modifier: Modifier = Modifier) {
  VerticalDivider(
    modifier = modifier,
    color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
  )
}
