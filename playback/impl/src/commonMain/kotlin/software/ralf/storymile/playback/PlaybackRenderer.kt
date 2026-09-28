package software.ralf.storymile.playback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.templates.LocalPlaybackBottomInset
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.appLayerShadow

@ContributesRenderer
class PlaybackRenderer : ComposeRenderer<PlaybackPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: PlaybackPresenterImpl.Model, modifier: Modifier) {
    val expanded = LocalScreenSize.current.category != ScreenSize.Category.PHONE
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Surface(
      modifier = modifier.fillMaxWidth().testTag("playback").appLayerShadow(shape),
      shape = shape,
      color = AppTheme.colorScheme.surface,
      border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
      Box(
        Modifier.padding(bottom = LocalPlaybackBottomInset.current)
          .fillMaxWidth()
          .height(if (expanded) 96.dp else 64.dp)
      )
    }
  }
}
