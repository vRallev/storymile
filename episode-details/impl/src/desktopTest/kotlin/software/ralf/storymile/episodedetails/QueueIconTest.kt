package software.ralf.storymile.episodedetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import kotlin.math.abs
import kotlin.test.Test

class QueueIconTest {
  @Test
  fun `queue icon morphs between Material endpoints and reverses in flight`() {
    listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
      runDesktopComposeUiTest(width = 160, height = 96) {
        val queued = mutableStateOf(false)
        setContent {
          CompositionLocalProvider(
            LocalContentColor provides Color.Black,
            LocalLayoutDirection provides direction,
          ) {
            Row(Modifier.background(Color.White)) {
              QueueIcon(isQueued = queued.value, modifier = Modifier.testTag("queue-icon"))
              Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                modifier = Modifier.testTag("playlist-add"),
              )
              Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.testTag("check"),
              )
            }
          }
        }

        val playlistAdd = image("playlist-add")
        val check = image("check")
        assertThat(difference(actual = image(), expected = playlistAdd)).isLessThan(0.01f)
        mainClock.autoAdvance = false
        try {
          runOnUiThread { queued.value = true }
          mainClock.advanceTimeByFrame()
          mainClock.advanceTimeBy(128)
          val intermediate = image()
          assertThat(hasMovingInk(intermediate = intermediate, start = playlistAdd, end = check))
            .isTrue()

          runOnUiThread { queued.value = false }
          mainClock.advanceTimeByFrame()
          assertThat(difference(actual = image(), expected = intermediate)).isLessThan(0.04f)
          mainClock.advanceTimeBy(500)
          assertThat(difference(actual = image(), expected = playlistAdd)).isLessThan(0.01f)

          runOnUiThread { queued.value = true }
          mainClock.advanceTimeByFrame()
          mainClock.advanceTimeBy(500)
          assertThat(difference(actual = image(), expected = check)).isLessThan(0.01f)
        } finally {
          mainClock.autoAdvance = true
        }
      }
    }
  }

  private fun ComposeUiTest.image(tag: String = "queue-icon") =
    onNodeWithTag(tag, useUnmergedTree = true).captureToImage()

  private fun hasMovingInk(
    intermediate: ImageBitmap,
    start: ImageBitmap,
    end: ImageBitmap,
  ): Boolean {
    val intermediatePixels = intermediate.toPixelMap()
    val startPixels = start.toPixelMap()
    val endPixels = end.toPixelMap()
    for (y in 0 until intermediate.height) {
      for (x in 0 until intermediate.width) {
        // A crossfade cannot paint outside both endpoint silhouettes.
        if (
          intermediatePixels[x, y].red < 0.2f &&
            startPixels[x, y].red > 0.8f &&
            endPixels[x, y].red > 0.8f
        ) {
          return true
        }
      }
    }
    return false
  }

  private fun difference(actual: ImageBitmap, expected: ImageBitmap): Float {
    val actualPixels = actual.toPixelMap()
    val expectedPixels = expected.toPixelMap()
    var difference = 0f
    for (y in 0 until actualPixels.height) {
      for (x in 0 until actualPixels.width) {
        difference += abs(actualPixels[x, y].red - expectedPixels[x, y].red)
      }
    }
    return difference / (actualPixels.width * actualPixels.height)
  }
}
