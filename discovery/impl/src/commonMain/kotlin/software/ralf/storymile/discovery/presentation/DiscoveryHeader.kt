package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
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
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.back
import software.ralf.storymile.discovery.impl.generated.resources.profile
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun DiscoveryHeader(title: String, backTag: String, onBack: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack, modifier = Modifier.testTag(backTag)) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
    }
    Text(
      text = title,
      modifier = Modifier.weight(1f).semantics { heading() },
      style = AppTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      color = AppTheme.colorScheme.onSurface,
    )
    IconButton(onClick = {}, modifier = Modifier.testTag("tab-profile")) {
      Icon(
        imageVector = Icons.Filled.AccountCircle,
        contentDescription = stringResource(Res.string.profile),
        modifier = Modifier.size(40.dp),
        tint = AppTheme.colorScheme.outline,
      )
    }
  }
}
