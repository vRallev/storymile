package software.ralf.storymile.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
    val tablet = LocalScreenSize.current.category != ScreenSize.Category.PHONE
    Box(
      modifier.fillMaxWidth().height(if (tablet) 96.dp else 64.dp).padding(horizontal = 24.dp),
      contentAlignment = Alignment.CenterStart,
    ) {
      PlaybackArtwork(
        modifier = Modifier.size(if (tablet) 72.dp else 40.dp),
        thumbnail = true,
      )
    }
  }
}
