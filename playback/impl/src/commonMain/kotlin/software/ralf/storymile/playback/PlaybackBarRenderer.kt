package software.ralf.storymile.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize

@ContributesRenderer
class PlaybackBarRenderer : ComposeRenderer<PlaybackPresenterImpl.BarModel>() {
  @Composable
  override fun Compose(model: PlaybackPresenterImpl.BarModel, modifier: Modifier) {
    val size = LocalScreenSize.current
    val tablet = size.category != ScreenSize.Category.PHONE
    val wide = size.category == ScreenSize.Category.TABLET_LARGE
    val player = model.player
    var panel by remember { mutableStateOf<PlaybackOptions.Panel?>(null) }
    Row(
      modifier.fillMaxWidth().height(if (tablet) 96.dp else 64.dp).padding(horizontal = 24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (tablet) 16.dp else 12.dp),
    ) {
      PlaybackArtwork(Modifier.size(if (tablet) 72.dp else 40.dp), thumbnail = true)
      Column(if (wide) Modifier.weight(0.8f) else Modifier.weight(1f)) {
        PlaybackControls.Labels(player, compact = !tablet)
        if (tablet && !wide) {
          PlaybackControls.Seek(
            player,
            Modifier.fillMaxWidth().height(36.dp),
            inline = true,
            tag = "playback-bar-seek",
            expanded = false,
          )
        }
      }
      if (wide) {
        PlaybackControls.Seek(
          player,
          Modifier.weight(1f),
          inline = true,
          tag = "playback-bar-seek",
          expanded = false,
        )
        PlaybackControls.Skip(player, forward = false, compact = true)
      }
      PlaybackControls.PlayPause(
        player,
        size = if (tablet) 56.dp else 40.dp,
        tag = "playback-bar-play-pause",
        expanded = false,
      )
      if (wide) {
        PlaybackControls.Skip(player, forward = true, compact = true)
      }
      if (tablet) {
        PlaybackSpeed.Button(
          player,
          onClick = { panel = PlaybackOptions.Panel.SPEED },
          modifier = Modifier.widthIn(min = 56.dp),
          expanded = false,
          showLabel = wide,
        )
      }
    }
    PlaybackOptions.Dialog(panel, player, expanded = false, onDismiss = { panel = null })
  }
}
