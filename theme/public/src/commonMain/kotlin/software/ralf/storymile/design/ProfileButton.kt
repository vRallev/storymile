package software.ralf.storymile.design

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.generated.resources.Res
import software.ralf.storymile.theme.generated.resources.profile

/** Shows the shared profile placeholder with a localized accessibility label. */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  IconButton(onClick = onClick, modifier = modifier.size(48.dp), enabled = enabled) {
    Icon(
      imageVector = Icons.Filled.AccountCircle,
      contentDescription = stringResource(Res.string.profile),
      modifier = Modifier.size(44.dp),
      tint = AppTheme.colorScheme.outline,
    )
  }
}
