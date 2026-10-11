package software.ralf.storymile.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.downloads.impl.generated.resources.Res
import software.ralf.storymile.downloads.impl.generated.resources.discover_podcasts
import software.ralf.storymile.downloads.impl.generated.resources.download_illustration
import software.ralf.storymile.downloads.impl.generated.resources.downloads
import software.ralf.storymile.downloads.impl.generated.resources.downloads_description
import software.ralf.storymile.downloads.impl.generated.resources.downloads_hint
import software.ralf.storymile.downloads.impl.generated.resources.downloads_summary
import software.ralf.storymile.downloads.impl.generated.resources.listen_anywhere
import software.ralf.storymile.downloads.impl.generated.resources.profile
import software.ralf.storymile.downloads.impl.generated.resources.storymile
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileLogo

@ContributesRenderer
class DownloadsRenderer : ComposeRenderer<DownloadsPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: DownloadsPresenterImpl.Model, modifier: Modifier) {
    val window = LocalScreenSize.current
    val compactWidth = window.width < 600.dp
    val showBrand = window.width < 1200.dp && window.height > window.width
    BoxWithConstraints(modifier.fillMaxSize().testTag("downloads-screen")) {
      val short = maxHeight < 480.dp
      val horizontal = short && maxWidth >= 560.dp
      val horizontalPadding = if (compactWidth || short) 20.dp else 32.dp
      val verticalPadding = if (short) 12.dp else 24.dp
      val headerSpacing = if (short) 20.dp else 32.dp
      val bottomSpacing = if (short) 12.dp else 48.dp
      val density = LocalDensity.current
      var headerHeight by remember { mutableStateOf(0.dp) }
      val minimumContentHeight =
        (maxHeight - verticalPadding * 2 - headerHeight - headerSpacing - bottomSpacing)
          .coerceAtLeast(0.dp)
      Column(
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = horizontalPadding, vertical = verticalPadding),
      ) {
        Column(Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
          Header(showBrand = showBrand, compact = compactWidth || short)
        }
        Spacer(Modifier.height(headerSpacing))
        Box(
          Modifier.fillMaxWidth().heightIn(min = minimumContentHeight),
          contentAlignment = Alignment.Center,
        ) {
          if (horizontal) {
            Row(
              modifier = Modifier.widthIn(max = 760.dp).testTag("downloads-empty"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
              DownloadIllustration(compact = true)
              EmptyMessage(compact = true, modifier = Modifier.weight(1f))
            }
          } else {
            Column(
              modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag("downloads-empty"),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              DownloadIllustration(compact = compactWidth || short)
              Spacer(Modifier.height(if (short) 16.dp else 24.dp))
              EmptyMessage(compact = compactWidth || short)
            }
          }
        }
        Spacer(Modifier.height(bottomSpacing))
      }
    }
  }

  @Composable
  private fun Header(showBrand: Boolean, compact: Boolean) {
    if (showBrand) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        StorymileLogo(Modifier.size(if (compact) 44.dp else 56.dp))
        Text(
          text = stringResource(Res.string.storymile),
          style = AppTheme.typography.titleLarge,
          color = AppTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        Profile()
      }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(Res.string.downloads),
          style =
            if (compact) AppTheme.typography.headlineLarge else AppTheme.typography.displayLarge,
          fontWeight = FontWeight.Bold,
          color = AppTheme.colorScheme.onBackground,
          modifier = Modifier.semantics { heading() },
        )
        Text(
          text = stringResource(Res.string.downloads_summary),
          style = if (compact) AppTheme.typography.bodyLarge else AppTheme.typography.headlineSmall,
          color = AppTheme.colorScheme.secondary,
        )
      }
      if (!showBrand) {
        Profile()
      }
    }
  }

  @Composable
  private fun Profile() {
    IconButton(onClick = {}, modifier = Modifier.testTag("downloads-profile")) {
      Icon(
        imageVector = Icons.Filled.AccountCircle,
        contentDescription = stringResource(Res.string.profile),
        tint = AppTheme.colorScheme.outline,
        modifier = Modifier.size(44.dp),
      )
    }
  }

  @Composable
  private fun DownloadIllustration(compact: Boolean) {
    Box(
      modifier =
        Modifier.size(if (compact) 128.dp else 176.dp)
          .background(AppTheme.colorScheme.primaryContainer, CircleShape)
          .testTag("downloads-illustration"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(Res.drawable.download_illustration),
        contentDescription = null,
        tint = AppTheme.colorScheme.primary,
        modifier = Modifier.size(if (compact) 72.dp else 96.dp),
      )
    }
  }

  @Composable
  private fun EmptyMessage(compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = stringResource(Res.string.listen_anywhere),
        style =
          if (compact) AppTheme.typography.headlineMedium else AppTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = AppTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
      )
      Spacer(Modifier.height(12.dp))
      Text(
        text = stringResource(Res.string.downloads_description),
        style = if (compact) AppTheme.typography.bodyLarge else AppTheme.typography.headlineSmall,
        color = AppTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = if (compact) 300.dp else 600.dp),
      )
      Spacer(Modifier.height(24.dp))
      Button(
        onClick = {},
        modifier =
          Modifier.widthIn(max = if (compact) 264.dp else 336.dp)
            .fillMaxWidth()
            .heightIn(min = if (compact) 48.dp else 56.dp)
            .testTag("downloads-discover"),
      ) {
        Text(
          stringResource(Res.string.discover_podcasts),
          style =
            if (compact) AppTheme.typography.titleMedium else AppTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )
      }
      Spacer(Modifier.height(20.dp))
      Text(
        text = stringResource(Res.string.downloads_hint),
        style = if (compact) AppTheme.typography.bodyMedium else AppTheme.typography.bodyLarge,
        color = AppTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
      )
    }
  }
}
