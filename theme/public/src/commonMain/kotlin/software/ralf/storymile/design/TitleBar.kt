package software.ralf.storymile.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.generated.resources.Res
import software.ralf.storymile.theme.generated.resources.back

/**
 * Shows a localized [title], back action, and profile placeholder. Owns the bar's content padding;
 * place it above the screen's scrollable content.
 */
@Composable
fun TitleBar(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  backButtonModifier: Modifier = Modifier,
  titleModifier: Modifier = Modifier,
  profileIconModifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier.fillMaxWidth().testTag("title-bar")) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = if (maxWidth < 600.dp) 16.dp else 32.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = backButtonModifier.size(48.dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
      }
      Text(
        text = title,
        modifier =
          Modifier.weight(1f).then(titleModifier).testTag("title-bar-title").semantics {
            heading()
          },
        style = AppTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = AppTheme.colorScheme.onSurface,
      )
      ProfileButton(
        onClick = {},
        modifier = Modifier.testTag("tab-profile"),
        iconModifier = profileIconModifier,
      )
    }
  }
}
