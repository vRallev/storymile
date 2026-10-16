package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun PodcastArtwork(podcast: DiscoveredPodcast, modifier: Modifier) {
  Box(
    modifier =
      modifier.clip(RoundedCornerShape(12.dp)).background(AppTheme.colorScheme.secondaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.Podcasts,
      contentDescription = null,
      modifier = Modifier.fillMaxSize(0.4f),
      tint = AppTheme.colorScheme.onSecondaryContainer,
    )
    if (podcast.artworkUrl != null) {
      AsyncImage(
        model =
          ImageRequest.Builder(LocalPlatformContext.current)
            .data(podcast.artworkUrl)
            .memoryCacheKey(podcast.artworkUrl)
            .placeholderMemoryCacheKey(podcast.artworkUrl)
            .build(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    }
  }
}
