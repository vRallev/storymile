@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class PlaybackTransitionTest {
  @Test
  fun `exclusive content fades with the sheet and hidden controls ignore touch`() =
    runDesktopComposeUiTest(width = 160, height = 80) {
      var progress by mutableFloatStateOf(0f)
      val transition = PlaybackTransition { progress }
      val clicks = mutableMapOf(false to 0, true to 0)
      setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          Row(Modifier.fillMaxSize().background(Color.White)) {
            listOf(false, true).forEach { expanded ->
              with(transition) {
                Box(
                  Modifier.fade(expanded).size(80.dp).background(Color.Black).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                  ) {
                    clicks[expanded] = clicks.getValue(expanded) + 1
                  },
                )
              }
            }
          }
        }
      }

      var collapsedClicks = 0
      var expandedClicks = 0
      listOf(0f, 0.5f, 1f, 0f).forEach { fraction ->
        runOnIdle { progress = fraction }
        val pixels = onRoot().captureToImage().toPixelMap()
        assertGrayscale(pixels[40, 40], fraction)
        assertGrayscale(pixels[120, 40], 1f - fraction)

        onRoot().performTouchInput {
          click(Offset(40f, 40f))
          click(Offset(120f, 40f))
        }
        if (fraction < 1f) {
          collapsedClicks++
        }
        if (fraction > 0f) {
          expandedClicks++
        }
        runOnIdle {
          assertThat(clicks.getValue(false)).isEqualTo(collapsedClicks)
          assertThat(clicks.getValue(true)).isEqualTo(expandedClicks)
        }
      }
    }

  private fun assertGrayscale(color: Color, expected: Float) {
    listOf(color.red, color.green, color.blue).forEach {
      assertTrue(abs(it - expected) <= 0.01f, "Expected gray $expected, got $color")
    }
    assertThat(color.alpha).isEqualTo(1f)
  }
}
