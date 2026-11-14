package software.ralf.storymile.shows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import kotlin.math.abs
import kotlin.test.Test

class FollowIconTest {
  @Test
  fun `icon morphs in both directions and matches the Material endpoints`() =
    runDesktopComposeUiTest(width = 160, height = 96) {
      val followed = mutableStateOf(false)
      setContent {
        CompositionLocalProvider(LocalContentColor provides Color.Black) {
          Row(Modifier.background(Color.White)) {
            FollowIcon(followed.value)
            Icon(Icons.Filled.Add, null, Modifier.testTag("plus"))
            Icon(Icons.Filled.Check, null, Modifier.testTag("check"))
          }
        }
      }
      val plus = image("plus")
      val check = image("check")
      assertMatches(actual = image(), expected = plus)
      mainClock.autoAdvance = false
      try {
        listOf(true, false).forEach { target ->
          runOnUiThread { followed.value = target }
          mainClock.advanceTimeByFrame()
          mainClock.advanceTimeBy(128)
          val intermediate = image()
          assertThat(difference(actual = intermediate, expected = plus)).isGreaterThan(0.02f)
          assertThat(difference(actual = intermediate, expected = check)).isGreaterThan(0.02f)
          val pixels = intermediate.toPixelMap()
          val plusPixels = plus.toPixelMap()
          val checkPixels = check.toPixelMap()
          var newInk = 0
          for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
              if (
                pixels[x, y].red < 0.2f &&
                  plusPixels[x, y].red > 0.8f &&
                  checkPixels[x, y].red > 0.8f
              ) {
                newInk++
              }
            }
          }
          // Moving geometry paints outside both endpoint silhouettes; a crossfade cannot.
          assertThat(newInk).isGreaterThan(0)
          mainClock.advanceTimeBy(500)
          assertMatches(actual = image(), expected = if (target) check else plus)
        }
      } finally {
        mainClock.autoAdvance = true
      }
    }

  @Test
  fun `restored state does not replay and an interrupted morph reverses continuously`() =
    runDesktopComposeUiTest(width = 160, height = 96) {
      val followed = mutableStateOf(true)
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(LocalContentColor provides Color.Black) {
          Row(Modifier.background(Color.White)) {
            FollowIcon(followed.value)
            Icon(Icons.Filled.Check, null, Modifier.testTag("check"))
          }
        }
      }
      try {
        val check = image("check")
        assertMatches(actual = image(), expected = check)
        runOnUiThread { followed.value = false }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(128)
        val interrupted = image()
        assertThat(difference(actual = interrupted, expected = check)).isGreaterThan(0.02f)
        runOnUiThread { followed.value = true }
        mainClock.advanceTimeByFrame()
        // One frame can finish before Compose observes the reversed target.
        assertThat(difference(actual = image(), expected = interrupted)).isLessThan(0.04f)
        mainClock.advanceTimeBy(500)
        assertMatches(actual = image(), expected = check)
      } finally {
        mainClock.autoAdvance = true
      }
    }

  private fun ComposeUiTest.image(tag: String = "show-follow-icon") =
    onNodeWithTag(tag, useUnmergedTree = true).captureToImage()

  private fun assertMatches(actual: ImageBitmap, expected: ImageBitmap) {
    assertThat(actual.width).isEqualTo(expected.width)
    assertThat(actual.height).isEqualTo(expected.height)
    assertThat(difference(actual = actual, expected = expected)).isLessThan(0.01f)
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
