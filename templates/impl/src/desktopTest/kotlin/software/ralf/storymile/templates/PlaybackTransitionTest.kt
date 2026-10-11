@file:OptIn(ExperimentalTestApi::class, ExperimentalSharedTransitionApi::class)

package software.ralf.storymile.templates

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

  @Test
  fun `shared controls move outside faded clipped parents and leave no endpoint ghosts`() =
    runDesktopComposeUiTest(width = 240, height = 160) {
      var progress by mutableFloatStateOf(0f)
      val transition = PlaybackTransition { progress }
      val clicks = mutableMapOf(false to 0, true to 0)
      setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          SharedTransitionLayout(Modifier.fillMaxSize().background(Color.White)) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
              listOf(false, true).forEach { expanded ->
                val side = if (expanded) 40.dp else 20.dp
                val position = if (expanded) 160.dp else 0.dp
                with(transition) {
                  Box(
                    Modifier.offset(position, if (expanded) 100.dp else 0.dp)
                      .fade(expanded)
                      .size(side)
                      .clipToBounds(),
                  ) {
                    SharedElement("control", expanded, CornerSize(0.dp)) {
                      Box(
                        Modifier.size(side).background(Color.Black).clickable(
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
          }
        }
      }

      listOf(0f, 0.25f, 0.5f, 0.75f, 0.25f, 1f, 0f).forEachIndexed { index, fraction ->
        runOnIdle { progress = fraction }
        val pixels = onRoot().captureToImage().toPixelMap()
        val left = (160 * fraction).toInt()
        val top = (100 * fraction).toInt()
        val side = (20 + 20 * fraction).toInt()
        assertTrue(
          pixels[left + side / 2, top + side / 2].red < 0.01f,
          "Expected an opaque shared control at step $index progress $fraction, got ${pixels[left + side / 2, top + side / 2]}",
        )
        assertGrayscale(pixels[left + side + 2, top + side / 2], 1f)
        if (fraction == 0f) {
          assertGrayscale(pixels[180, 120], 1f)
        } else if (fraction == 1f) {
          assertGrayscale(pixels[10, 10], 1f)
          assertGrayscale(pixels[50, 40], 1f)
        }
      }
      onRoot().performTouchInput {
        click(Offset(10f, 10f))
        click(Offset(180f, 120f))
      }
      runOnIdle {
        assertThat(clicks.getValue(false)).isEqualTo(1)
        assertThat(clicks.getValue(true)).isEqualTo(0)
        progress = 1f
      }
      onRoot().performTouchInput {
        click(Offset(10f, 10f))
        click(Offset(180f, 120f))
      }
      runOnIdle {
        assertThat(clicks.getValue(false)).isEqualTo(1)
        assertThat(clicks.getValue(true)).isEqualTo(1)
      }
    }

  @Test
  fun `shared text draws one opaque interpolated style without duplicate glyphs`() =
    runDesktopComposeUiTest(width = 320, height = 200) {
      var progress by mutableFloatStateOf(0f)
      val transition = PlaybackTransition { progress }
      val start =
        TextStyle(
          color = Color.Black,
          fontFamily = FontFamily.Monospace,
          fontSize = 20.sp,
          fontWeight = FontWeight.Normal,
          letterSpacing = 0.sp,
          lineHeight = 26.sp,
        )
      val end =
        start.copy(
          fontSize = 40.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 4.sp,
          lineHeight = 50.sp,
        )
      val middle =
        start.copy(
          fontSize = 30.sp,
          fontWeight = FontWeight(550),
          letterSpacing = 2.sp,
          lineHeight = 38.sp,
        )
      setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          SharedTransitionLayout(Modifier.fillMaxSize().background(Color.White)) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
              listOf(false, true).forEach { expanded ->
                val style = if (expanded) end else start
                with(transition) {
                  Box(
                    Modifier.offset(if (expanded) 160.dp else 0.dp, if (expanded) 80.dp else 0.dp)
                      .fade(expanded),
                  ) {
                    SharedText("text", expanded, "HHH", style) {
                      BasicText("HHH", style = style)
                    }
                  }
                }
              }
              BasicText("HHH", style = middle, modifier = Modifier.offset(80.dp, 120.dp))
            }
          }
        }
      }

      runOnIdle { progress = 0.5f }
      val pixels = onRoot().captureToImage().toPixelMap()
      val actual = ink(pixels, 40)
      val expected = ink(pixels, 120)
      assertTrue(actual.minimum < 0.05f, "Shared glyphs must remain opaque during motion")
      assertTrue(abs(actual.width - expected.width) <= 2)
      assertTrue(abs(actual.height - expected.height) <= 2)
      assertTrue(actual.coverage / expected.coverage in 0.85f..1.15f)

      runOnIdle { progress = 1f }
      assertGrayscale(onRoot().captureToImage().toPixelMap()[90, 45], 1f)
      runOnIdle { progress = 0f }
      assertGrayscale(onRoot().captureToImage().toPixelMap()[180, 100], 1f)
    }

  @Test
  fun `fully scrolled out shared endpoints fade without appearing outside the viewport`() {
    listOf(false, true).forEach { text ->
      runDesktopComposeUiTest(width = 240, height = 160) {
        var progress by mutableFloatStateOf(0f)
        var scroll by mutableFloatStateOf(0f)
        val transition = PlaybackTransition { progress }
        setContent {
          CompositionLocalProvider(LocalDensity provides Density(1f)) {
            SharedTransitionLayout(Modifier.fillMaxSize().background(Color.White)) {
              CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                listOf(false, true).forEach { expanded ->
                  val side = if (expanded) 40.dp else 20.dp
                  with(transition) {
                    Box(
                      Modifier.offset(
                          if (expanded) 160.dp else 0.dp,
                          if (expanded) 100.dp else 0.dp,
                        )
                        .fade(expanded)
                        .size(side)
                        .clipToBounds(),
                    ) {
                      val modifier = Modifier.offset(y = if (expanded) -scroll.dp else 0.dp)
                      val content: @Composable BoxScope.() -> Unit = {
                        Box(Modifier.size(side).background(Color.Black))
                      }
                      if (text) {
                        SharedText(
                          "scrolled-control",
                          expanded,
                          "H",
                          TextStyle(
                            color = Color.Black,
                            background = Color.Black,
                            fontSize = side.value.sp,
                          ),
                          modifier = modifier,
                          content = content,
                        )
                      } else {
                        SharedElement(
                          "scrolled-control",
                          expanded,
                          CornerSize(0.dp),
                          modifier = modifier,
                          content = content,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        runOnIdle { progress = 1f }
        assertGrayscale(onRoot().captureToImage().toPixelMap()[180, 120], 0f)
        runOnIdle { scroll = 60f }
        assertGrayscale(onRoot().captureToImage().toPixelMap()[180, 60], 1f)
        listOf(0.99f, 0.5f, 0f).forEach { fraction ->
          runOnIdle { progress = fraction }
          val pixels = onRoot().captureToImage().toPixelMap()
          val side = 20 + 20 * fraction
          val x = (160 * fraction + side / 2).toInt()
          val y = (40 * fraction + side / 2).toInt()
          if (text && fraction > 0f && fraction < 1f) {
            val minimum =
              (0 until side.toInt()).minOf { dy ->
                (0 until side.toInt()).minOf { dx ->
                  pixels[(160 * fraction).toInt() + dx, (40 * fraction).toInt() + dy].red
                }
              }
            assertTrue(abs(minimum - fraction) <= 0.02f)
          } else {
            assertGrayscale(pixels[x, y], fraction)
          }
        }
      }
    }
  }

  private fun ink(pixels: androidx.compose.ui.graphics.PixelMap, top: Int): Ink {
    val points = mutableListOf<Pair<Int, Int>>()
    var coverage = 0f
    var minimum = 1f
    for (y in top until top + 60) {
      for (x in 70 until 160) {
        val value = pixels[x, y].red
        coverage += 1f - value
        minimum = minOf(minimum, value)
        if (value < 0.95f) {
          points += x to y
        }
      }
    }
    assertTrue(points.isNotEmpty())
    return Ink(
      points.maxOf { it.first } - points.minOf { it.first },
      points.maxOf { it.second } - points.minOf { it.second },
      coverage,
      minimum,
    )
  }

  private fun assertGrayscale(color: Color, expected: Float) {
    listOf(color.red, color.green, color.blue).forEach {
      assertTrue(abs(it - expected) <= 0.01f, "Expected gray $expected, got $color")
    }
    assertThat(color.alpha).isEqualTo(1f)
  }

  private data class Ink(val width: Int, val height: Int, val coverage: Float, val minimum: Float)
}
