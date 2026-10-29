package software.ralf.storymile.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.LocalOnToggleTheme
import software.ralf.storymile.theme.generated.resources.Res
import software.ralf.storymile.theme.generated.resources.storymile

/**
 * Shared tab title and profile control. Shows the app brand in tall phone layouts, using the full
 * [windowWidth] to avoid repeating the navigation sidebar's brand. [title] is localized by the
 * caller. [appBarTitleModifier] applies to the brand name, or to [title] when the brand is hidden.
 */
@Composable
fun TabHeader(
  title: String,
  windowWidth: Dp,
  modifier: Modifier = Modifier,
  appBarTitleModifier: Modifier = Modifier,
  profileIconModifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier.fillMaxWidth().testTag("tab-header")) {
    // AGP lint resolves the public API to a restricted JVM typealias.
    @Suppress("RestrictedApi") val fontScale = LocalDensity.current.fontScale
    val availableHeight = maxHeight / fontScale
    val tight = availableHeight < 700.dp
    val compact = maxWidth < 600.dp || availableHeight < 600.dp
    val showBrand = windowWidth < 600.dp && availableHeight >= 400.dp
    Column(
      Modifier.fillMaxWidth()
        .padding(
          start = if (maxWidth < 600.dp) 20.dp else 32.dp,
          end = if (maxWidth < 600.dp) 20.dp else 32.dp,
          top = if (tight) 8.dp else 24.dp,
          bottom = if (tight) 8.dp else 20.dp,
        ),
    ) {
      if (showBrand) {
        Row(
          Modifier.fillMaxWidth().testTag("tab-brand"),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StorymileLogo(Modifier.size(44.dp).testTag("tab-brand-logo"))
          Text(
            text = stringResource(Res.string.storymile),
            modifier =
              Modifier.weight(1f)
                .then(appBarTitleModifier)
                .testTag("tab-brand-name")
                .hiddenLongPress(LocalOnToggleTheme.current),
            style = AppTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          ProfileButton(
            onClick = {},
            modifier = Modifier.testTag("tab-profile"),
            iconModifier = profileIconModifier,
          )
        }
        Spacer(Modifier.height(if (tight) 8.dp else 20.dp))
      }
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          modifier =
            Modifier.weight(1f)
              .then(if (showBrand) Modifier else appBarTitleModifier)
              .testTag("tab-title")
              .semantics { heading() },
          style =
            when {
              fontScale > 1.3f -> AppTheme.typography.headlineMedium
              compact -> AppTheme.typography.headlineLarge
              else -> AppTheme.typography.displaySmall
            },
          fontWeight = FontWeight.Bold,
          color = AppTheme.colorScheme.onBackground,
        )
        if (!showBrand) {
          ProfileButton(
            onClick = {},
            modifier = Modifier.testTag("tab-profile"),
            iconModifier = profileIconModifier,
          )
        }
      }
    }
  }
}
