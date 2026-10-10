package software.ralf.storymile.playback

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.collapse_player
import software.ralf.storymile.playback.impl.generated.resources.more_options
import software.ralf.storymile.playback.impl.generated.resources.now_playing
import software.ralf.storymile.playback.impl.generated.resources.profile
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.templates.LocalCollapsePlayback
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class PlaybackScreenRenderer : ComposeRenderer<PlaybackPresenterImpl.ScreenModel>() {
  @Composable
  override fun Compose(model: PlaybackPresenterImpl.ScreenModel, modifier: Modifier) {
    val screenSize = LocalScreenSize.current
    val tablet = screenSize.width >= 600.dp
    Column(modifier.fillMaxSize()) {
      PlayerHeader(
        tablet = tablet,
        showProfile = screenSize.category != ScreenSize.Category.PHONE,
        landscape = screenSize.width > screenSize.height,
      )
      Spacer(Modifier.height(if (screenSize.height < 600.dp) 8.dp else 24.dp))
      BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val landscape = maxWidth >= 600.dp && screenSize.width > screenSize.height
        val padding = if (landscape) 32.dp else 24.dp
        val availableWidth = (maxWidth - padding * 2).coerceAtLeast(0.dp)
        val artworkWidth =
          when {
            landscape -> availableWidth * 0.44f
            tablet -> maxWidth * 0.7f
            else -> availableWidth
          }
        val artworkSize = minOf(artworkWidth, (maxHeight - 24.dp).coerceAtLeast(0.dp))
        PlaybackArtwork(
          Modifier.align(if (landscape) Alignment.TopStart else Alignment.TopCenter)
            .padding(start = if (landscape) padding else 0.dp)
            .size(artworkSize)
            .testTag("playback-artwork-expanded"),
        )
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun PlayerHeader(tablet: Boolean, showProfile: Boolean, landscape: Boolean) {
    val onCollapse = LocalCollapsePlayback.current
    TopAppBar(
      title = {
        Text(
          text = stringResource(Res.string.now_playing),
          modifier = Modifier.padding(start = if (tablet) 24.dp else 16.dp),
          style = if (tablet) AppTheme.typography.titleLarge else AppTheme.typography.headlineSmall,
        )
      },
      navigationIcon = {
        IconButton(onClick = onCollapse, modifier = Modifier.testTag("playback-collapse")) {
          Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(Res.string.collapse_player),
            modifier = Modifier.size(32.dp),
          )
        }
      },
      actions = {
        if (showProfile && landscape) {
          ProfilePlaceholder()
        }
        IconButton(
          onClick = {},
          enabled = false,
          colors =
            IconButtonDefaults.iconButtonColors(
              disabledContentColor = AppTheme.colorScheme.primary,
            ),
        ) {
          Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(Res.string.more_options),
            modifier = Modifier.size(32.dp),
          )
        }
        if (showProfile && !landscape) {
          ProfilePlaceholder()
        }
      },
      modifier = Modifier.padding(horizontal = if (tablet) 16.dp else 0.dp),
      colors =
        TopAppBarDefaults.topAppBarColors(
          containerColor = AppTheme.colorScheme.surface,
          titleContentColor = AppTheme.colorScheme.onSurface,
          navigationIconContentColor = AppTheme.colorScheme.primary,
        ),
      windowInsets = WindowInsets(0),
    )
  }

  @Composable
  private fun ProfilePlaceholder() {
    IconButton(onClick = {}, enabled = false) {
      Icon(
        imageVector = Icons.Default.AccountCircle,
        contentDescription = stringResource(Res.string.profile),
        modifier = Modifier.size(40.dp),
      )
    }
  }
}
