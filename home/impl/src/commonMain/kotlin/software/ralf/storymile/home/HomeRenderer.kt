package software.ralf.storymile.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.TabHeader
import software.ralf.storymile.home.impl.generated.resources.Res
import software.ralf.storymile.home.impl.generated.resources.discover_podcasts
import software.ralf.storymile.home.impl.generated.resources.explore_topics
import software.ralf.storymile.home.impl.generated.resources.find_your_first_podcast
import software.ralf.storymile.home.impl.generated.resources.home_illustration
import software.ralf.storymile.home.impl.generated.resources.listen_without_account
import software.ralf.storymile.home.impl.generated.resources.podcasts_and_episodes
import software.ralf.storymile.home.impl.generated.resources.welcome_to_storymile
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class HomeRenderer : ComposeRenderer<HomePresenterImpl.Model>() {
  @Composable
  override fun Compose(model: HomePresenterImpl.Model, modifier: Modifier) {
    Box(modifier.fillMaxSize().testTag("tab-content")) {
      BoxWithConstraints(Modifier.fillMaxSize().testTag("home-screen")) {
        val wide =
          maxWidth >= 760.dp &&
            LocalScreenSize.current.orientation == ScreenSize.Orientation.LANDSCAPE
        val phone = maxWidth < 600.dp
        val compact = maxHeight < 700.dp || LocalDensity.current.fontScale > 1.3f
        val promptSpacing =
          when {
            maxHeight / LocalDensity.current.fontScale < 400.dp -> 4.dp
            compact -> 8.dp
            else -> 12.dp
          }
        Column(Modifier.fillMaxSize()) {
          TabHeader(
            title = stringResource(Res.string.welcome_to_storymile),
            windowWidth = LocalScreenSize.current.width,
          )
          Column(
            Modifier.weight(1f)
              .padding(horizontal = if (phone) 20.dp else 32.dp)
              .padding(bottom = if (compact) 8.dp else 24.dp),
          ) {
            Search()
            Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
            if (wide) {
              Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                  modifier =
                    Modifier.fillMaxWidth().padding(vertical = if (compact) 0.dp else 24.dp),
                  horizontalArrangement = Arrangement.spacedBy(32.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Illustration(Modifier)
                  }
                  Prompt(
                    wide = true,
                    compact = compact,
                    spacing = promptSpacing,
                    modifier = Modifier.weight(1f),
                  )
                }
              }
            } else {
              Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                Illustration(
                  Modifier.weight(1f, fill = false).widthIn(max = if (phone) 320.dp else 480.dp),
                )
                Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                Prompt(
                  wide = false,
                  compact = compact,
                  spacing = promptSpacing,
                  modifier =
                    (if (compact) Modifier else Modifier.widthIn(max = 440.dp)).fillMaxWidth(),
                )
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun Search() {
    Surface(
      onClick = {},
      modifier = Modifier.fillMaxWidth().testTag("home-search"),
      shape = RoundedCornerShape(12.dp),
      color = AppTheme.colorScheme.surface,
      contentColor = AppTheme.colorScheme.onSurfaceVariant,
      border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
      Row(
        modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
        Text(
          text = stringResource(Res.string.podcasts_and_episodes),
          style = AppTheme.typography.bodyLarge,
        )
      }
    }
  }

  @Composable
  private fun Illustration(modifier: Modifier) {
    Image(
      painter = painterResource(Res.drawable.home_illustration),
      contentDescription = null,
      modifier = modifier.testTag("home-illustration"),
    )
  }

  @Composable
  private fun Prompt(wide: Boolean, compact: Boolean, spacing: Dp, modifier: Modifier) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
      Text(
        text = stringResource(Res.string.find_your_first_podcast),
        modifier = Modifier.testTag("home-prompt"),
        style =
          when {
            (wide && compact) || largeText -> AppTheme.typography.headlineSmall
            wide -> AppTheme.typography.displaySmall
            else -> AppTheme.typography.headlineMedium
          },
        fontWeight = FontWeight.Bold,
        color = AppTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )
      Text(
        text = stringResource(Res.string.explore_topics),
        modifier = if (compact) Modifier else Modifier.widthIn(max = if (wide) 440.dp else 290.dp),
        style =
          if (wide && !compact) {
            AppTheme.typography.headlineSmall
          } else {
            AppTheme.typography.bodyLarge
          },
        fontWeight = FontWeight.Normal,
        color = AppTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      if (!compact) {
        Spacer(Modifier.height(8.dp))
      }
      DiscoverButton(wide = wide, compact = compact)
      if (!compact) {
        Spacer(Modifier.height(4.dp))
      }
      Text(
        text = stringResource(Res.string.listen_without_account),
        style = if (wide) AppTheme.typography.bodyLarge else AppTheme.typography.bodyMedium,
        color = AppTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }

  @Composable
  private fun DiscoverButton(wide: Boolean, compact: Boolean) {
    Button(
      onClick = {},
      modifier =
        Modifier.widthIn(max = if (wide || compact) 440.dp else 340.dp)
          .fillMaxWidth()
          .heightIn(min = if (compact) 48.dp else 56.dp)
          .testTag("home-discover"),
      shape = CircleShape,
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = if (compact) 12.dp else 16.dp),
    ) {
      Text(
        text = stringResource(Res.string.discover_podcasts),
        style =
          if (wide && !compact) {
            AppTheme.typography.headlineSmall
          } else {
            AppTheme.typography.titleMedium
          },
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.width(8.dp))
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
      )
    }
  }
}
