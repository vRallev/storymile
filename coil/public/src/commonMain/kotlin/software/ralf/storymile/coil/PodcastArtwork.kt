package software.ralf.storymile.coil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter.State
import coil3.compose.CrossfadePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.useExistingImageAsPlaceholder
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.crossfade
import software.ralf.storymile.theme.AppTheme

/**
 * Displays podcast or episode artwork, fading it in when loaded. Keeps loaded artwork visible while
 * a new URL loads for the same [artworkId].
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun PodcastArtwork(artworkUrl: String?, modifier: Modifier, artworkId: String? = artworkUrl) =
  key(artworkId) {
    Box(
      modifier =
        modifier
          .clip(RoundedCornerShape(12.dp))
          .background(AppTheme.colorScheme.secondaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Podcasts,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(0.4f),
        tint = AppTheme.colorScheme.onSecondaryContainer,
      )
      if (artworkUrl != null) {
        var loadingPainter by remember { mutableStateOf<Painter?>(null) }
        AsyncImage(
          model =
            ImageRequest.Builder(LocalPlatformContext.current)
              .data(artworkUrl)
              .crossfade(false)
              .memoryCacheKey(artworkUrl)
              .placeholderMemoryCacheKey(artworkUrl)
              .useExistingImageAsPlaceholder(true)
              .build(),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
          transform = { state ->
            val previousPainter = loadingPainter
            loadingPainter = (state as? State.Loading)?.painter
            if (state is State.Success && state.result.dataSource != DataSource.MEMORY_CACHE) {
              state.copy(
                painter =
                  CrossfadePainter(
                    start = previousPainter,
                    end = state.painter,
                    contentScale = ContentScale.Crop,
                    // An opaque start cover keeps the fallback icon from showing through.
                    fadeStart = false,
                  ),
              )
            } else {
              state
            }
          },
        )
      }
    }
  }
