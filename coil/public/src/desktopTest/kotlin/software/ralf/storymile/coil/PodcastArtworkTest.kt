package software.ralf.storymile.coil

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.memory.MemoryCache
import coil3.size.Size
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@OptIn(DelicateCoilApi::class)
class PodcastArtworkTest {
  @Test
  fun `the thumbnail loads at its own size and upgrades when larger artwork is cached`() {
    runDesktopComposeUiTest(width = 200, height = 200) {
      val url = "https://example.invalid/cover.png"
      val smallSize = with(density) { 40.dp.roundToPx() }
      val largeSize = with(density) { 120.dp.roundToPx() }
      val smallRequest = CompletableDeferred<Size>()
      val largeRequest = CompletableDeferred<Size>()
      val largeArtworkReady = CompletableDeferred<Unit>()
      val requestCount = MutableStateFlow(0)
      val imageLoader =
        ImageLoader.Builder(PlatformContext.INSTANCE)
          .diskCache(null)
          .components {
            add(
              Fetcher.Factory<Uri> { _, options, _ ->
                Fetcher {
                  requestCount.update { it + 1 }
                  val small = options.size == Size(width = smallSize, height = smallSize)
                  val color = if (small) Color.Red else Color.Green
                  val edge = if (small) smallSize else largeSize
                  if (small) {
                    smallRequest.complete(options.size)
                  } else {
                    largeRequest.complete(options.size)
                    largeArtworkReady.await()
                  }
                  ImageFetchResult(
                    image = ColorImage(color = color.toArgb(), width = edge, height = edge),
                    isSampled = small,
                    dataSource = DataSource.NETWORK,
                  )
                }
              },
            )
          }
          .build()
      withImageLoader(imageLoader) {
        var showLargeArtwork by mutableStateOf(false)
        setContent {
          MaterialTheme {
            Row {
              PodcastArtwork(
                artworkUrl = url,
                modifier = Modifier.size(40.dp).testTag("thumbnail"),
                thumbnailSize = 40.dp,
              )
              if (showLargeArtwork) {
                PodcastArtwork(
                  artworkUrl = url,
                  modifier = Modifier.size(120.dp).testTag("expanded"),
                )
              }
            }
          }
        }
        fun thumbnailColor() = onNodeWithTag("thumbnail").captureToImage().toPixelMap()[20, 20]

        waitUntil { thumbnailColor() == Color.Red }
        assertThat(smallRequest.getCompleted())
          .isEqualTo(Size(width = smallSize, height = smallSize))
        runOnIdle { showLargeArtwork = true }
        waitUntil { largeRequest.isCompleted }
        assertThat(largeRequest.getCompleted())
          .isEqualTo(Size(width = largeSize, height = largeSize))
        assertThat(thumbnailColor()).isEqualTo(Color.Red)
        val expanded = onNodeWithTag("expanded").captureToImage().toPixelMap()
        assertThat(expanded[60, 60]).isEqualTo(Color.Red)
        largeArtworkReady.complete(Unit)
        waitUntil { thumbnailColor() == Color.Green }
        assertThat(requestCount.value).isEqualTo(2)
      }
    }
  }

  @Test
  fun `the thumbnail uses already cached larger artwork`() {
    val url = "https://example.invalid/cached.png"
    val fetched = CompletableDeferred<Unit>()
    val imageLoader =
      ImageLoader.Builder(PlatformContext.INSTANCE)
        .diskCache(null)
        .components {
          add(
            Fetcher.Factory<Uri> { _, _, _ ->
              Fetcher {
                fetched.complete(Unit)
                ImageFetchResult(
                  image = ColorImage(color = Color.Red.toArgb(), width = 40, height = 40),
                  isSampled = true,
                  dataSource = DataSource.NETWORK,
                )
              }
            },
          )
        }
        .build()
    checkNotNull(imageLoader.memoryCache)[MemoryCache.Key(url)] =
      MemoryCache.Value(ColorImage(color = Color.Green.toArgb(), width = 512, height = 512))
    withImageLoader(imageLoader) {
      runDesktopComposeUiTest(width = 200, height = 200) {
        setContent {
          MaterialTheme {
            PodcastArtwork(
              artworkUrl = url,
              modifier = Modifier.size(40.dp).testTag("thumbnail"),
              thumbnailSize = 40.dp,
            )
          }
        }
        val thumbnail = onNodeWithTag("thumbnail").captureToImage().toPixelMap()
        assertThat(thumbnail[20, 20]).isEqualTo(Color.Green)
        assertThat(fetched.isCompleted).isEqualTo(false)
      }
    }
  }

  @Test
  fun `the placeholder does not show while loaded artwork is replaced`() {
    assertReplacement(samePodcast = true)
  }

  @Test
  fun `selecting another podcast clears the previous artwork`() {
    assertReplacement(samePodcast = false)
  }

  private fun assertReplacement(samePodcast: Boolean) {
    val replacementStarted = CompletableDeferred<Unit>()
    val replacementReady = CompletableDeferred<Unit>()
    val imageLoader =
      ImageLoader.Builder(PlatformContext.INSTANCE)
        .diskCache(null)
        .components {
          add(
            Fetcher.Factory<Uri> { data, _, _ ->
              Fetcher {
                val color =
                  if (data.toString().endsWith(".jpg")) {
                    replacementStarted.complete(Unit)
                    replacementReady.await()
                    Color.Green
                  } else {
                    Color.Red
                  }
                ImageFetchResult(
                  image = ColorImage(color = color.toArgb(), width = 100, height = 100),
                  isSampled = false,
                  dataSource = DataSource.NETWORK,
                )
              }
            },
          )
        }
        .build()
    withImageLoader(imageLoader) {
      runDesktopComposeUiTest(width = 200, height = 200) {
        var artworkUrl by mutableStateOf("https://example.invalid/cover.png")
        var artworkId by mutableStateOf("first")
        setContent {
          MaterialTheme(colorScheme = lightColorScheme(secondaryContainer = Color.Blue)) {
            PodcastArtwork(
              artworkUrl = artworkUrl,
              modifier = Modifier.size(100.dp).testTag("artwork"),
              artworkId = artworkId,
            )
          }
        }
        fun artworkColor() = onNodeWithTag("artwork").captureToImage().toPixelMap()[20, 20]

        waitUntil { artworkColor() == Color.Red }
        runOnIdle {
          artworkUrl = "https://example.invalid/cover.jpg"
          if (!samePodcast) {
            artworkId = "second"
          }
        }
        waitUntil { replacementStarted.isCompleted }
        waitForIdle()
        if (samePodcast) {
          assertThat(artworkColor()).isEqualTo(Color.Red)
        } else {
          assertThat(artworkColor()).isNotEqualTo(Color.Red)
        }
        replacementReady.complete(Unit)
        waitUntil {
          val color = artworkColor()
          if (samePodcast) {
            assertThat(color.blue, "fallback showing through the artwork").isEqualTo(0f)
          }
          color == Color.Green
        }
      }
    }
  }

  private fun withImageLoader(imageLoader: ImageLoader, block: () -> Unit) {
    val previousImageLoader = SingletonImageLoader.get(PlatformContext.INSTANCE)
    SingletonImageLoader.setUnsafe(imageLoader)
    try {
      block()
    } finally {
      SingletonImageLoader.setUnsafe(previousImageLoader)
      imageLoader.shutdown()
    }
  }
}
