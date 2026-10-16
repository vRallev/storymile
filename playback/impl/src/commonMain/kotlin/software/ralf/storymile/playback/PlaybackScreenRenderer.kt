package software.ralf.storymile.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.ProfileButton
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.collapse_player
import software.ralf.storymile.playback.impl.generated.resources.more_options
import software.ralf.storymile.playback.impl.generated.resources.now_playing
import software.ralf.storymile.playback.impl.generated.resources.playback_failed
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.templates.LocalCollapsePlayback
import software.ralf.storymile.templates.LocalPlaybackTransition
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class PlaybackScreenRenderer : ComposeRenderer<PlaybackPresenterImpl.ScreenModel>() {
  @Composable
  override fun Compose(model: PlaybackPresenterImpl.ScreenModel, modifier: Modifier) {
    val screenSize = LocalScreenSize.current
    val tablet = screenSize.width >= 600.dp
    val controlsScroll = rememberScrollState()
    var panel by remember { mutableStateOf<PlaybackOptions.Panel?>(null) }
    val transition = LocalPlaybackTransition.current
    val fade = with(transition) { if (this == null) Modifier else Modifier.fade(expanded = true) }
    Column(modifier.fillMaxSize()) {
      PlayerHeader(
        tablet = tablet,
        showProfile = screenSize.category != ScreenSize.Category.PHONE,
        landscape = screenSize.width > screenSize.height,
        onMore = { panel = PlaybackOptions.Panel.INFO },
      )
      Spacer(Modifier.height(if (screenSize.height < 600.dp) 8.dp else 24.dp))
      BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val landscape = maxWidth >= 600.dp && screenSize.width > screenSize.height
        val largeControls = tablet && maxWidth >= 1000.dp
        if (landscape) {
          val artworkSize =
            minOf((maxWidth - 64.dp) * 0.44f, (maxHeight - 24.dp).coerceAtLeast(0.dp))
          Row(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
          ) {
            PlaybackArtwork(Modifier.size(artworkSize))
            PlayerDetails(
              model.player,
              largeControls,
              fade.weight(1f).verticalScroll(controlsScroll).padding(top = 16.dp, bottom = 24.dp),
            ) {
              panel = it
            }
          }
        } else {
          val available = if (tablet) maxWidth * 0.7f else (maxWidth - 48.dp).coerceAtLeast(0.dp)
          Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Measure the controls first, then fit the artwork into the remaining height.
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
              PlaybackArtwork(Modifier.size(minOf(available, maxHeight)))
            }
            Spacer(Modifier.height(20.dp))
            PlayerDetails(
              model.player,
              tablet,
              fade
                .fillMaxWidth()
                .verticalScroll(controlsScroll)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
              contentWidth = if (tablet) available else Dp.Unspecified,
            ) {
              panel = it
            }
          }
        }
      }
    }
    PlaybackOptions.Dialog(panel, model.player, expanded = true, onDismiss = { panel = null })
  }

  @Composable
  private fun PlayerDetails(
    player: PlaybackPresenterImpl.PlayerModel,
    large: Boolean,
    modifier: Modifier,
    contentWidth: Dp = Dp.Unspecified,
    onPanel: (PlaybackOptions.Panel) -> Unit,
  ) {
    Column(
      modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(
        if (contentWidth.isSpecified) Modifier.widthIn(max = contentWidth)
        else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        PlaybackControls.Details(player, large)
        PlaybackControls.Seek(player, Modifier.fillMaxWidth())
        PlaybackControls.Transport(player, large)
        if (player.error != null) {
          Text(
            stringResource(Res.string.playback_failed),
            color = AppTheme.colorScheme.error,
            modifier = Modifier.testTag("playback-error"),
          )
        }
        PlaybackOptions.Actions(player, onPanel)
      }
      PlaybackOptions.Chapter(player) { onPanel(PlaybackOptions.Panel.CHAPTERS) }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun PlayerHeader(
    tablet: Boolean,
    showProfile: Boolean,
    landscape: Boolean,
    onMore: () -> Unit,
  ) {
    val onCollapse = LocalCollapsePlayback.current
    val transition = LocalPlaybackTransition.current
    val fade =
      with(transition) {
        if (this == null) Modifier else Modifier.fade(expanded = true, startFraction = 0.5f)
      }
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
          ProfileButton(onClick = {}, enabled = false)
        }
        IconButton(
          onClick = onMore,
          modifier = Modifier.testTag("playback-more"),
        ) {
          Icon(
            Icons.Default.MoreVert,
            contentDescription = stringResource(Res.string.more_options),
            modifier = Modifier.size(32.dp),
          )
        }
        if (showProfile && !landscape) {
          ProfileButton(onClick = {}, enabled = false)
        }
      },
      modifier = fade.padding(horizontal = if (tablet) 16.dp else 0.dp).testTag("playback-header"),
      colors =
        TopAppBarDefaults.topAppBarColors(
          containerColor = AppTheme.colorScheme.surface,
          titleContentColor = AppTheme.colorScheme.onSurface,
          navigationIconContentColor = AppTheme.colorScheme.primary,
        ),
      windowInsets = WindowInsets(0),
    )
  }
}
