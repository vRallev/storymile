package software.ralf.storymile.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import software.ralf.storymile.design.TabHeader
import software.ralf.storymile.downloads.impl.generated.resources.Res
import software.ralf.storymile.downloads.impl.generated.resources.discover_podcasts
import software.ralf.storymile.downloads.impl.generated.resources.download_illustration
import software.ralf.storymile.downloads.impl.generated.resources.downloads
import software.ralf.storymile.downloads.impl.generated.resources.downloads_description
import software.ralf.storymile.downloads.impl.generated.resources.downloads_hint
import software.ralf.storymile.downloads.impl.generated.resources.downloads_summary
import software.ralf.storymile.downloads.impl.generated.resources.listen_anywhere
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class DownloadsRenderer : ComposeRenderer<DownloadsPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: DownloadsPresenterImpl.Model, modifier: Modifier) {
    val window = LocalScreenSize.current
    BoxWithConstraints(modifier.fillMaxSize().testTag("downloads-screen")) {
      // AGP lint resolves the public API to a restricted JVM typealias.
      @Suppress("RestrictedApi") val short = maxHeight / LocalDensity.current.fontScale < 600.dp
      val compact = window.width < 600.dp || short
      val horizontal = short && maxWidth >= 560.dp
      val sidePadding = if (maxWidth < 600.dp) 20.dp else 32.dp
      Column(Modifier.fillMaxSize()) {
        TabHeader(title = stringResource(Res.string.downloads), windowWidth = window.width)
        Column(
          Modifier.weight(1f)
            .padding(horizontal = sidePadding)
            .padding(bottom = if (short) 12.dp else 24.dp),
        ) {
          Text(
            text = stringResource(Res.string.downloads_summary),
            style =
              if (compact) AppTheme.typography.bodyLarge else AppTheme.typography.headlineSmall,
            color = AppTheme.colorScheme.secondary,
          )
          Spacer(Modifier.height(if (short) 12.dp else 32.dp))
          Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (horizontal) {
              Row(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("downloads-empty"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
              ) {
                DownloadIllustration(compact = true)
                EmptyMessage(compact = true, tight = true, modifier = Modifier.weight(1f))
              }
            } else {
              Column(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().testTag("downloads-empty"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                DownloadIllustration(compact, Modifier.weight(1f, fill = false))
                Spacer(Modifier.height(if (short) 12.dp else 24.dp))
                EmptyMessage(compact, tight = short)
              }
            }
          }
          Spacer(Modifier.height(if (short) 12.dp else 48.dp))
        }
      }
    }
  }

  @Composable
  private fun DownloadIllustration(compact: Boolean, modifier: Modifier = Modifier) {
    Box(
      modifier =
        modifier
          .sizeIn(
            maxWidth = if (compact) 128.dp else 176.dp,
            maxHeight = if (compact) 128.dp else 176.dp,
          )
          .aspectRatio(1f, matchHeightConstraintsFirst = true)
          .background(AppTheme.colorScheme.primaryContainer, CircleShape)
          .testTag("downloads-illustration"),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(Res.drawable.download_illustration),
        contentDescription = null,
        tint = AppTheme.colorScheme.primary,
        modifier = Modifier.fillMaxSize(0.55f),
      )
    }
  }

  @Composable
  private fun EmptyMessage(compact: Boolean, tight: Boolean, modifier: Modifier = Modifier) {
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
      Spacer(Modifier.height(if (tight) 8.dp else 12.dp))
      Text(
        text = stringResource(Res.string.downloads_description),
        style = if (compact) AppTheme.typography.bodyLarge else AppTheme.typography.headlineSmall,
        color = AppTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = if (compact && !tight) 300.dp else 600.dp),
      )
      Spacer(Modifier.height(if (tight) 8.dp else 24.dp))
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
      Spacer(Modifier.height(if (tight) 8.dp else 20.dp))
      Text(
        text = stringResource(Res.string.downloads_hint),
        style = if (compact) AppTheme.typography.bodyMedium else AppTheme.typography.bodyLarge,
        color = AppTheme.colorScheme.secondary,
        textAlign = TextAlign.Center,
      )
    }
  }
}
