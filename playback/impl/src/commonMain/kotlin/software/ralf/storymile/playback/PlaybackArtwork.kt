package software.ralf.storymile.playback

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.preview_artwork
import software.ralf.storymile.playback.impl.generated.resources.quiet_current_placeholder

@Composable
internal fun PlaybackArtwork(modifier: Modifier = Modifier, thumbnail: Boolean = false) {
  // Temporary cover from design-research/screens/android-phone-player-light.png.
  Image(
    painter = painterResource(Res.drawable.quiet_current_placeholder),
    contentDescription = stringResource(Res.string.preview_artwork),
    contentScale = ContentScale.Crop,
    modifier = modifier.clip(if (thumbnail) RoundedCornerShape(8.dp) else RoundedCornerShape(5)),
  )
}
