package software.ralf.storymile.coil

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred

@OptIn(DelicateCoilApi::class)
class PodcastArtworkTest {
  @Test
  fun `loaded artwork stays visible while its URL changes`() {
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
    val previousImageLoader = SingletonImageLoader.get(PlatformContext.INSTANCE)
    SingletonImageLoader.setUnsafe(imageLoader)
    try {
      runDesktopComposeUiTest(width = 200, height = 200) {
        var artworkUrl by mutableStateOf("https://example.invalid/cover.png")
        var artworkId by mutableStateOf("first")
        setContent {
          MaterialTheme {
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
        waitUntil { artworkColor() == Color.Green }
      }
    } finally {
      SingletonImageLoader.setUnsafe(previousImageLoader)
      imageLoader.shutdown()
    }
  }
}
