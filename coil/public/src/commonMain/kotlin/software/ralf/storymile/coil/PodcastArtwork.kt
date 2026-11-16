package software.ralf.storymile.coil

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import coil3.Image as CoilImage
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter.State
import coil3.compose.CrossfadePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.compose.useExistingImageAsPlaceholder
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import software.ralf.storymile.theme.AppTheme

/**
 * Displays podcast or episode artwork, fading it in when loaded.
 *
 * Regular artwork stays visible while a new URL loads for the same [artworkId]. Supply
 * [thumbnailSize] to display cached artwork at that size or better. A cache miss loads artwork at
 * the layout size.
 */
@Composable
fun PodcastArtwork(
  artworkUrl: String?,
  modifier: Modifier,
  artworkId: String? = artworkUrl,
  thumbnailSize: Dp = Dp.Unspecified,
) =
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
        val thumbnail = thumbnailSize.isSpecified
        val cachedArtwork =
          if (thumbnail) {
            rememberCachedThumbnailArtwork(artworkUrl = artworkUrl, thumbnailSize = thumbnailSize)
          } else {
            null
          }
        if (cachedArtwork != null) {
          Image(
            painter = cachedArtwork,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
        } else {
          LoadArtwork(artworkUrl = artworkUrl, thumbnail = thumbnail)
        }
      }
    }
  }

private val artworkCacheUpdates = MutableStateFlow(0L)

@Composable
private fun rememberCachedThumbnailArtwork(artworkUrl: String, thumbnailSize: Dp): Painter? {
  val context = LocalPlatformContext.current
  val cache = SingletonImageLoader.get(context).memoryCache ?: return null
  val thumbnailPixels = with(LocalDensity.current) { thumbnailSize.roundToPx() }
  val key = remember(artworkUrl) { MemoryCache.Key(artworkUrl) }
  var best by
    remember(cache, key, thumbnailPixels) {
      mutableStateOf(cache[key]?.image?.takeIf { it.shortEdge >= thumbnailPixels })
    }
  LaunchedEffect(cache, key, thumbnailPixels) {
    artworkCacheUpdates.collect {
      val image = cache[key]?.image
      if (
        image != null &&
          image.shortEdge >= thumbnailPixels &&
          image.shortEdge > (best?.shortEdge ?: 0)
      ) {
        best = image
      }
    }
  }
  return remember(best, context) { best?.asPainter(context) }
}

private val CoilImage.shortEdge: Int
  get() = minOf(width, height)

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun LoadArtwork(artworkUrl: String, thumbnail: Boolean) {
  var loadingPainter by remember { mutableStateOf<Painter?>(null) }
  val artworkKey = remember(artworkUrl) { MemoryCache.Key(artworkUrl) }
  // A small decode can finish after the full image. Keep their memory entries separate.
  val thumbnailKey =
    remember(artworkUrl) {
      MemoryCache.Key(key = artworkUrl, extras = mapOf("storymile.thumbnail" to "true"))
    }
  val cacheKey = if (thumbnail) thumbnailKey else artworkKey
  val fallbackKey = if (thumbnail) artworkKey else thumbnailKey
  AsyncImage(
    model =
      ImageRequest.Builder(LocalPlatformContext.current)
        .data(artworkUrl)
        .crossfade(false)
        .memoryCacheKey(cacheKey)
        .placeholderMemoryCacheKey(cacheKey)
        .placeholder { request ->
          SingletonImageLoader.get(request.context).memoryCache?.get(fallbackKey)?.image
        }
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
    onState = { state ->
      if (
        !thumbnail &&
          state is State.Success &&
          state.result.memoryCacheKey != null &&
          state.result.dataSource != DataSource.MEMORY_CACHE
      ) {
        artworkCacheUpdates.update { it + 1 }
      }
    },
  )
}
