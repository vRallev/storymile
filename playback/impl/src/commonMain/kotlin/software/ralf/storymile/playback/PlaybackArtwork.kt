package software.ralf.storymile.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.templates.LocalPlaybackTransition

@Composable
internal fun PlaybackArtwork(
  artworkUrl: String?,
  modifier: Modifier = Modifier,
  thumbnail: Boolean = false,
) {
  val transition = LocalPlaybackTransition.current
  val cornerSize = if (thumbnail) CornerSize(8.dp) else CornerSize(5)
  val tag = if (thumbnail) "playback-artwork-collapsed" else "playback-artwork-expanded"
  if (transition == null) {
    PodcastArtwork(
      artworkUrl = artworkUrl,
      modifier = modifier.clip(RoundedCornerShape(cornerSize)).testTag(tag),
    )
  } else {
    transition.SharedElement(
      key = "artwork",
      expanded = !thumbnail,
      cornerSize = cornerSize,
      modifier = modifier,
    ) {
      PodcastArtwork(artworkUrl = artworkUrl, modifier = Modifier.fillMaxSize().testTag(tag))
    }
  }
}
