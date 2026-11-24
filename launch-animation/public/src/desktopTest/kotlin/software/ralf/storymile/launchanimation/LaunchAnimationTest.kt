@file:OptIn(InternalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package software.ralf.storymile.launchanimation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.ui.graphics.luminance
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
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import kotlin.test.Test
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeEnvironment

class LaunchAnimationTest {
  private val themeEnvironment =
    object : ThemeEnvironment {
      @Composable
      override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(
          LocalSystemTheme provides if (darkTheme) SystemTheme.Dark else SystemTheme.Light,
          content = content,
        )
      }
    }

  @Test
  fun `the initial cover hides every pixel of the destination`() {
    val frames =
      listOf(Color.Magenta, Color.Green).map { destination ->
        var pixels = emptyList<Color>()
        runDesktopComposeUiTest(width = 400, height = 800) {
          mainClock.autoAdvance = false
          setContent {
            ThemedLaunch { Box(Modifier.fillMaxSize().background(destination)) }
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
          setContent { ThemedLaunch(dark = dark) { Destination() } }
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
  fun `the boat opens quickly and the remaining cover fades during the reveal`() {
    listOf(400 to 800, 1000 to 400).forEach { (width, height) ->
      runDesktopComposeUiTest(width = width, height = height) {
        mainClock.autoAdvance = false
        var progress by mutableStateOf(Animatable(0.62f))
        setContent {
          ThemedLaunch(progress = progress, contentReady = false) { Destination() }
        }

        val opening = onRoot().captureToImage().toPixelMap()
        val lowerOpening =
          (opening.height * 0.46f + minOf(opening.width, opening.height) / 4).toInt()
        assertThat(opening[opening.width / 2, lowerOpening]).isEqualTo(Color.Magenta)
        val coveredCorner = opening[0, opening.height - 1]
        assertThat(coveredCorner).isNotEqualTo(Color.Magenta)

        runOnIdle { progress = Animatable(0.67f) }
        mainClock.advanceTimeByFrame()
        val fading = onRoot().captureToImage().toPixelMap()
        assertThat(fading[0, fading.height - 1]).isNotEqualTo(coveredCorner)
        assertThat(fading[0, fading.height - 1]).isNotEqualTo(Color.Magenta)
      }
    }
  }

  @Test
  fun `theme changes keep the animation progressing and content stays composed`() =
    runDesktopComposeUiTest {
      mainClock.autoAdvance = false
      var dark by mutableStateOf(false)
      var creations = 0
      var disposals = 0
      setContent {
        ThemedLaunch(dark = dark) {
          remember { creations++ }
          DisposableEffect(Unit) { onDispose { disposals++ } }
          Destination()
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
  fun `host recreation resumes a pending reveal and never replays a completed launch`() =
    runDesktopComposeUiTest {
      mainClock.autoAdvance = false
      var progress by mutableStateOf(Animatable(0f))
      var host by mutableIntStateOf(0)
      var ready by mutableStateOf(false)
      setContent {
        key(host) {
          ThemedLaunch(progress = progress, contentReady = ready) { Destination() }
        }
      }
      mainClock.advanceTimeBy(3000)
      onNodeWithTag("launch-animation").assertIsDisplayed()
      assertCovered()

      runOnIdle { host++ }
      mainClock.advanceTimeBy(3000)
      onNodeWithTag("launch-animation").assertIsDisplayed()
      assertCovered()
      runOnIdle { ready = true }
      mainClock.advanceTimeBy(1300)
      onNodeWithTag("launch-animation").assertDoesNotExist()
      onNodeWithTag("destination").assertIsDisplayed()

      runOnIdle { host++ }
      mainClock.advanceTimeByFrame()
      onNodeWithTag("launch-animation").assertDoesNotExist()

      runOnIdle { progress = Animatable(0f) }
      mainClock.advanceTimeByFrame()
      onNodeWithTag("launch-animation").assertIsDisplayed()
      assertCovered()
    }

  @Test
  fun `the first cover uses the host theme and recolors before revealing ready content`() =
    runDesktopComposeUiTest(width = 400, height = 800) {
      mainClock.autoAdvance = false
      val settledProgress = 0.32f
      val progress = Animatable(settledProgress)
      val themeProgress = Animatable(0f)
      var host by mutableIntStateOf(0)
      setContent {
        key(host) {
          ThemedLaunch(
            dark = true,
            initialDark = false,
            progress = progress,
            themeProgress = themeProgress,
          ) {
            Destination()
          }
        }
      }

      fun coverLuminance(): Float = onRoot().captureToImage().toPixelMap()[0, 0].luminance()

      val initial = coverLuminance()
      assertThat(initial).isGreaterThan(0.7f)
      mainClock.advanceTimeBy(160)
      val blended = coverLuminance()
      assertThat(blended).isLessThan(initial)
      runOnIdle { assertThat(progress.value).isEqualTo(settledProgress) }
      onNodeWithTag("destination").assertDoesNotExist()
      assertCovered()

      runOnIdle { host++ }
      mainClock.advanceTimeByFrame()
      assertThat(coverLuminance()).isLessThan(initial)
      mainClock.advanceTimeBy(300)
      val recolored = coverLuminance()
      assertThat(recolored).isLessThan(0.1f)
      assertThat(blended).isGreaterThan(recolored)
      runOnIdle { assertThat(themeProgress.value).isEqualTo(1f) }

      mainClock.advanceTimeBy(900)
      onNodeWithTag("launch-animation").assertDoesNotExist()
      onNodeWithTag("destination").assertIsDisplayed()
    }

  @Test
  fun `a recreated host reports its first frame even when the cover already finished`() =
    runDesktopComposeUiTest {
      mainClock.autoAdvance = false
      val progress = Animatable(1f)
      var firstFrames = 0
      setContent {
        ThemedLaunch(
          progress = progress,
          contentReady = false,
          onFirstFrame = { firstFrames++ },
        ) {
          Destination()
        }
      }
      mainClock.advanceTimeBy(100)
      onNodeWithTag("destination").assertIsDisplayed()
      onNodeWithTag("launch-animation").assertDoesNotExist()
      runOnIdle { assertThat(firstFrames).isEqualTo(1) }
    }

  @Test
  fun `touches cannot activate content until the reveal ends`() = runDesktopComposeUiTest {
    mainClock.autoAdvance = false
    var clicks = 0
    setContent { ThemedLaunch { Destination { clicks++ } } }
    onNodeWithTag("launch-animation").performTouchInput { click() }
    runOnIdle { assertThat(clicks).isEqualTo(0) }
    mainClock.advanceTimeBy(1300)
    onNodeWithTag("destination").performClick()
    runOnIdle { assertThat(clicks).isEqualTo(1) }
  }

  @Test
  fun `disabled animations still wait for ready content but skip the motion duration`() =
    runDesktopComposeUiTest(
      effectContext =
        object : MotionDurationScale {
          override val scaleFactor = 0f
        },
    ) {
      mainClock.autoAdvance = false
      var ready by mutableStateOf(false)
      setContent {
        ThemedLaunch(dark = true, initialDark = false, contentReady = ready) { Destination() }
      }
      mainClock.advanceTimeBy(3000)
      onNodeWithTag("launch-animation").assertIsDisplayed()
      assertCovered()
      runOnIdle { ready = true }
      mainClock.advanceTimeBy(128)
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
  private fun ThemedLaunch(
    dark: Boolean = false,
    initialDark: Boolean = dark,
    progress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    themeProgress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
    contentReady: Boolean = true,
    onFirstFrame: () -> Unit = {},
    content: @Composable () -> Unit,
  ) {
    StorymileTheme(environment = themeEnvironment, darkTheme = dark) {
      LaunchAnimation(
        themeEnvironment = themeEnvironment,
        darkTheme = dark,
        initialDarkTheme = initialDark,
        progress = progress,
        themeProgress = themeProgress,
        contentReady = contentReady,
        onFirstFrame = onFirstFrame,
        content = content,
      )
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
