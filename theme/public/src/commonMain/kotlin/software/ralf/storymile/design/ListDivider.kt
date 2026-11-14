package software.ralf.storymile.design

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.storymile.theme.AppTheme

/**
 * Separates rows or sections with a subtle themed horizontal line. Use [modifier] to provide
 * spacing and content insets.
 */
@Composable
fun ListDivider(modifier: Modifier = Modifier) {
  HorizontalDivider(
    modifier = modifier,
    color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
  )
}
