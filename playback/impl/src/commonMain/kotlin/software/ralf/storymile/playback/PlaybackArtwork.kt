package software.ralf.storymile.playback

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.preview_artwork
import software.ralf.storymile.playback.impl.generated.resources.quiet_current_placeholder
import software.ralf.storymile.templates.LocalPlaybackTransition

@Composable
internal fun PlaybackArtwork(modifier: Modifier = Modifier, thumbnail: Boolean = false) {
  // Temporary cover from design-research/screens/android-phone-player-light.png.
  val transition = LocalPlaybackTransition.current
  val cornerSize = if (thumbnail) CornerSize(8.dp) else CornerSize(5)
  val tag = if (thumbnail) "playback-artwork-collapsed" else "playback-artwork-expanded"
  val image: @Composable (Modifier) -> Unit = { imageModifier ->
    Image(
      painter = painterResource(Res.drawable.quiet_current_placeholder),
      contentDescription = stringResource(Res.string.preview_artwork),
      contentScale = ContentScale.Crop,
      modifier = imageModifier.testTag(tag),
    )
  }
  if (transition == null) {
    image(modifier.clip(RoundedCornerShape(cornerSize)))
  } else {
    transition.SharedElement(
      key = "preview-artwork",
      expanded = !thumbnail,
      cornerSize = cornerSize,
      modifier = modifier,
    ) {
      image(Modifier.fillMaxSize())
    }
  }
}
