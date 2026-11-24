package software.ralf.storymile.launchanimation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.design.storymileLogoPainter
import software.ralf.storymile.launch.animation.generated.resources.Res
import software.ralf.storymile.launch.animation.generated.resources.launch_description
import software.ralf.storymile.launch.animation.generated.resources.launch_title
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileColorSchemes
import software.ralf.storymile.theme.ThemeEnvironment

private const val SETTLED_PROGRESS = 0.32f

/**
 * Draws the boat immediately, then waits for [contentReady] and a rendered content frame before
 * revealing [content]. Install inside Storymile's theme for [darkTheme]. The cover starts in
 * [initialDarkTheme] and changes to [darkTheme] before the reveal. [themeEnvironment] supplies the
 * qualified artwork for the initial cover. Content stays composed and drawn behind the cover, with
 * input and accessibility blocked until the reveal ends.
 *
 * Retain [progress] and [themeProgress] across host recreation to resume an interrupted launch.
 * Each starts at zero and finishes at one when its animation runs.
 *
 * [onFirstFrame] runs after the first cover frame, so callers can start app initialization without
 * delaying that frame. System animation settings affect motion, but never bypass readiness.
 */
@Composable
fun LaunchAnimation(
  themeEnvironment: ThemeEnvironment,
  darkTheme: Boolean,
  modifier: Modifier = Modifier,
  initialDarkTheme: Boolean = darkTheme,
  progress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
  themeProgress: Animatable<Float, AnimationVector1D> = remember { Animatable(0f) },
  contentReady: Boolean = true,
  onFirstFrame: () -> Unit = {},
  content: @Composable () -> Unit,
) {
  val running by remember(progress) { derivedStateOf { progress.value < 1f } }
  var firstFrameDrawn by remember(progress) { mutableStateOf(false) }
  var contentDrawn by remember(progress, contentReady) { mutableStateOf(false) }
  val currentOnFirstFrame by rememberUpdatedState(onFirstFrame)
  val palettesDiffer = initialDarkTheme != darkTheme
  val recoloring by
    remember(progress, themeProgress, palettesDiffer) {
      derivedStateOf {
        palettesDiffer && progress.value <= SETTLED_PROGRESS && themeProgress.value < 1f
      }
    }

  LaunchedEffect(progress) {
    snapshotFlow { firstFrameDrawn }.first { it }
    // Let the host present the cover before dispatching startup (also on single-threaded Wasm).
    withFrameNanos {}
    currentOnFirstFrame()
  }
  if (running) {
    AnimateLaunch(
      progress = progress,
      themeProgress = themeProgress,
      recoloring = recoloring,
      palettesDiffer = palettesDiffer,
      contentReady = contentReady,
      firstFrameDrawn = { firstFrameDrawn },
      contentDrawn = { contentDrawn },
    )
  }

  Box(
    modifier.then(if (running) Modifier.onPreviewKeyEvent { true } else Modifier).afterDrawIf(
      !firstFrameDrawn,
    ) {
      firstFrameDrawn = true
    },
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .then(if (running) Modifier.clearAndSetSemantics {} else Modifier)
          .afterDrawIf(running && contentReady && !contentDrawn) {
            if (!size.isEmpty()) {
              contentDrawn = true
            }
          },
      propagateMinConstraints = true,
    ) {
      content()
    }
    if (running) {
      LaunchCover(
        themeEnvironment = themeEnvironment,
        initialDarkTheme = initialDarkTheme,
        progress = progress,
        themeProgress = themeProgress,
        recoloring = recoloring,
      )
    }
  }
}

@Composable
private fun AnimateLaunch(
  progress: Animatable<Float, AnimationVector1D>,
  themeProgress: Animatable<Float, AnimationVector1D>,
  recoloring: Boolean,
  palettesDiffer: Boolean,
  contentReady: Boolean,
  firstFrameDrawn: () -> Boolean,
  contentDrawn: () -> Boolean,
) {
  val currentPalettesDiffer by rememberUpdatedState(palettesDiffer)
  if (recoloring) {
    LaunchedEffect(themeProgress) {
      snapshotFlow(firstFrameDrawn).first { it }
      themeProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(((1f - themeProgress.value) * 320).toInt(), easing = LinearEasing),
      )
    }
  }
  LaunchedEffect(progress, contentReady) {
    if (progress.value < SETTLED_PROGRESS) {
      progress.animateTo(
        targetValue = SETTLED_PROGRESS,
        animationSpec =
          tween(((SETTLED_PROGRESS - progress.value) * 1200).toInt(), easing = LinearEasing),
      )
    }
    snapshotFlow(contentDrawn).first { it }
    // Cross a full frame so layout-driven state changes can recompose and draw under the cover.
    withFrameNanos {}
    withFrameNanos {}
    if (progress.value <= SETTLED_PROGRESS) {
      snapshotFlow { !currentPalettesDiffer || themeProgress.value >= 1f }.first { it }
    }
    if (progress.value < 1f) {
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(((1f - progress.value) * 1200).toInt(), easing = LinearEasing),
      )
    }
  }
}

@Composable
private fun BoxScope.LaunchCover(
  themeEnvironment: ThemeEnvironment,
  initialDarkTheme: Boolean,
  progress: Animatable<Float, AnimationVector1D>,
  themeProgress: Animatable<Float, AnimationVector1D>,
  recoloring: Boolean,
) {
  val painter = storymileLogoPainter()
  val colors = AppTheme.colorScheme
  val title = stringResource(Res.string.launch_title)
  val description = stringResource(Res.string.launch_description)
  val textMeasurer = rememberTextMeasurer()
  val titleStyle = AppTheme.typography.titleLarge
  val wordmark =
    remember(textMeasurer, title, titleStyle) {
      textMeasurer.measure(text = title, style = titleStyle)
    }
  val initialColors =
    when {
      !recoloring -> colors
      initialDarkTheme -> StorymileColorSchemes.harborNight
      else -> StorymileColorSchemes.paperTide
    }
  val cover: @Composable (Painter?) -> Unit = { initialPainter ->
    val drawing =
      remember(painter, initialPainter, colors, initialColors, wordmark) {
        BoatLaunchDrawing(
          painter = painter,
          colors = colors,
          wordmark = wordmark,
          initialPainter = initialPainter,
          initialColors = initialColors,
        )
      }
    Canvas(
      Modifier.matchParentSize()
        .testTag("launch-animation")
        .semantics { contentDescription = description }
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
          }
        },
    ) {
      val blend = if (recoloring) FastOutSlowInEasing.transform(themeProgress.value) else 1f
      with(drawing) { draw(progress = progress.value, themeProgress = blend) }
    }
  }
  if (recoloring) {
    themeEnvironment.Provide(darkTheme = initialDarkTheme) { cover(storymileLogoPainter()) }
  } else {
    cover(null)
  }
}

private fun Modifier.afterDrawIf(enabled: Boolean, action: DrawScope.() -> Unit): Modifier {
  if (!enabled) {
    return this
  }
  return drawWithContent {
    drawContent()
    action()
  }
}
