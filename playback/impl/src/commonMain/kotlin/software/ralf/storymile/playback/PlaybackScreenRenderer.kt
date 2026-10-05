package software.ralf.storymile.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer

@ContributesRenderer
class PlaybackScreenRenderer : ComposeRenderer<PlaybackPresenterImpl.ScreenModel>() {
  @Composable
  override fun Compose(model: PlaybackPresenterImpl.ScreenModel, modifier: Modifier) {
    Box(modifier.fillMaxSize())
  }
}
