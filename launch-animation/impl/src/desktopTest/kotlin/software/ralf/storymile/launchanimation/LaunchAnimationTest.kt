@file:OptIn(InternalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package software.ralf.storymile.launchanimation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import kotlin.test.Test
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.testing.FakeThemeEnvironment

class LaunchAnimationTest {
  @Test
  fun `the initial cover hides every pixel of the destination`() {
    val frames =
      listOf(Color.Magenta, Color.Green).map { destination ->
        var pixels = emptyList<Color>()
        runDesktopComposeUiTest(width = 400, height = 800) {
          mainClock.autoAdvance = false
          val animation: LaunchAnimation = DefaultLaunchAnimation()
          setContent {
            Theme { animation.Content { Box(Modifier.fillMaxSize().background(destination)) } }
          }
          val image = onRoot().captureToImage().toPixelMap()
          pixels = List(image.width * image.height) { image[it % image.width, it / image.width] }
        }
        pixels
      }
    assertThat(frames[0].size).isEqualTo(frames[1].size)
    val mismatchedPixels = frames[0].indices.count { frames[0][it] != frames[1][it] }
    assertThat(mismatchedPixels).isEqualTo(0)
  }

  @Test
  fun `both themes reveal content through the boat and clear every window edge`() {
    listOf(false, true).forEach { dark ->
      listOf(400 to 800, 1000 to 400).forEach { (width, height) ->
        runDesktopComposeUiTest(width = width, height = height) {
          mainClock.autoAdvance = false
          val animation: LaunchAnimation = DefaultLaunchAnimation()
          setContent { Theme(dark) { animation.Content { Destination() } } }
          onNodeWithTag("launch-animation").assertIsDisplayed()
          onNodeWithTag("destination").assertDoesNotExist()
          assertCovered()

          mainClock.advanceTimeBy(650)
          val revealing = onRoot().captureToImage().toPixelMap()
          assertThat(revealing[revealing.width / 2, (revealing.height * 0.46f).toInt()])
            .isEqualTo(Color.Magenta)
          assertThat(revealing[0, 0]).isNotEqualTo(Color.Magenta)

          mainClock.advanceTimeBy(700)
          onNodeWithTag("launch-animation").assertDoesNotExist()
          onNodeWithTag("destination").assertIsDisplayed()
          val revealed = onRoot().captureToImage().toPixelMap()
          listOf(
              0 to 0,
              revealed.width - 1 to 0,
              0 to revealed.height - 1,
              revealed.width - 1 to revealed.height - 1,
            )
            .forEach { (x, y) ->
              assertThat(revealed[x, y]).isEqualTo(Color.Magenta)
            }
        }
      }
    }
  }

  @Test
  fun `theme changes keep the animation progressing and content stays composed`() =
    runDesktopComposeUiTest {
      mainClock.autoAdvance = false
      val animation: LaunchAnimation = DefaultLaunchAnimation()
      var dark by mutableStateOf(false)
      var creations = 0
      var disposals = 0
      setContent {
        Theme(dark) {
          animation.Content {
            remember { creations++ }
            DisposableEffect(Unit) { onDispose { disposals++ } }
            Destination()
          }
        }
      }
      mainClock.advanceTimeBy(650)
      runOnIdle { dark = true }
      mainClock.advanceTimeByFrame()
      val revealing = onRoot().captureToImage().toPixelMap()
      assertThat(revealing[revealing.width / 2, (revealing.height * 0.46f).toInt()])
        .isEqualTo(Color.Magenta)
      mainClock.advanceTimeBy(700)
      runOnIdle { dark = false }
      mainClock.advanceTimeByFrame()
      onNodeWithTag("launch-animation").assertDoesNotExist()
      runOnIdle {
        assertThat(creations).isEqualTo(1)
        assertThat(disposals).isEqualTo(0)
      }
    }

  @Test
  fun `host recreation cannot replay an interrupted launch but a new app scope can`() =
    runDesktopComposeUiTest {
      mainClock.autoAdvance = false
      var animation: LaunchAnimation by mutableStateOf(DefaultLaunchAnimation())
      var host by mutableIntStateOf(0)
      setContent { key(host) { Theme { animation.Content { Destination() } } } }
      mainClock.advanceTimeBy(100)
      onNodeWithTag("launch-animation").assertIsDisplayed()

      runOnIdle { host++ }
      mainClock.advanceTimeByFrame()
      onNodeWithTag("launch-animation").assertDoesNotExist()
      onNodeWithTag("destination").assertIsDisplayed()

      runOnIdle { animation = DefaultLaunchAnimation() }
      mainClock.advanceTimeByFrame()
      onNodeWithTag("launch-animation").assertIsDisplayed()
      assertCovered()
    }

  @Test
  fun `touches cannot activate content until the reveal ends`() = runDesktopComposeUiTest {
    mainClock.autoAdvance = false
    val animation: LaunchAnimation = DefaultLaunchAnimation()
    var clicks = 0
    setContent { Theme { animation.Content { Destination { clicks++ } } } }
    onNodeWithTag("launch-animation").performTouchInput { click() }
    runOnIdle { assertThat(clicks).isEqualTo(0) }
    mainClock.advanceTimeBy(1300)
    onNodeWithTag("destination").performClick()
    runOnIdle { assertThat(clicks).isEqualTo(1) }
  }

  @Test
  fun `disabled system animations reveal content without waiting for the duration`() =
    runDesktopComposeUiTest(
      effectContext =
        object : MotionDurationScale {
          override val scaleFactor = 0f
        },
    ) {
      mainClock.autoAdvance = false
      val animation: LaunchAnimation = DefaultLaunchAnimation()
      setContent { Theme { animation.Content { Destination() } } }
      mainClock.advanceTimeBy(64)
      onNodeWithTag("launch-animation").assertDoesNotExist()
      onNodeWithTag("destination").assertIsDisplayed()
    }

  private fun ComposeUiTest.assertCovered() {
    val pixels = onRoot().captureToImage().toPixelMap()
    assertThat(pixels[0, 0]).isNotEqualTo(Color.Magenta)
    assertThat(pixels[pixels.width / 2, (pixels.height * 0.46f).toInt()])
      .isNotEqualTo(Color.Magenta)
  }

  @Composable
  private fun Theme(dark: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(
      LocalSystemTheme provides if (dark) SystemTheme.Dark else SystemTheme.Light,
    ) {
      StorymileTheme(environment = FakeThemeEnvironment(), darkTheme = dark, content = content)
    }
  }

  @Composable
  private fun Destination(onClick: () -> Unit = {}) {
    Box(
      Modifier.fillMaxSize()
        .background(Color.Magenta)
        .testTag("destination")
        .clickable(onClick = onClick),
    )
  }
}
