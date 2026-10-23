package software.ralf.storymile.launchanimation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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

/**
 * Owns one cold-launch reveal without depending on the app scope. Retain this instance across host
 * recreation. An interrupted reveal resumes; a completed reveal does not replay.
 */
class LaunchAnimation {
  private val progress = Animatable(0f)

  /**
   * Draws the boat immediately, then waits for [contentReady] and a rendered content frame before
   * revealing [content]. Install inside Storymile's theme. Content stays composed, measured, and
   * drawn behind the cover, with input and accessibility blocked until the reveal ends.
   *
   * [onFirstFrame] runs after the first cover frame, so callers can start app initialization
   * without delaying that frame. System animation settings affect motion, but never bypass
   * readiness.
   */
  @Composable
  fun Content(
    modifier: Modifier = Modifier,
    contentReady: Boolean = true,
    onFirstFrame: () -> Unit = {},
    content: @Composable () -> Unit,
  ) {
    val running by remember(this) { derivedStateOf { progress.value < 1f } }
    var firstFrameDrawn by remember(this) { mutableStateOf(false) }
    var contentDrawn by remember(this, contentReady) { mutableStateOf(false) }
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrame)

    LaunchedEffect(this) {
      snapshotFlow { firstFrameDrawn }.first { it }
      // Let the host present the cover before dispatching startup (also on single-threaded Wasm).
      withFrameNanos {}
      currentOnFirstFrame()
    }
    LaunchedEffect(this, contentReady) {
      if (progress.value < 0.32f) {
        progress.animateTo(
          targetValue = 0.32f,
          animationSpec = tween(((0.32f - progress.value) * 1200).toInt(), easing = LinearEasing),
        )
      }
      snapshotFlow { contentDrawn }.first { it }
      // Cross a full frame so layout-driven state changes can recompose and draw under the cover.
      withFrameNanos {}
      withFrameNanos {}
      if (progress.value < 1f) {
        progress.animateTo(
          targetValue = 1f,
          animationSpec = tween(((1f - progress.value) * 1200).toInt(), easing = LinearEasing),
        )
      }
    }

    Box(
      modifier
        .onPreviewKeyEvent { running }
        .drawWithContent {
          drawContent()
          if (!firstFrameDrawn) {
            firstFrameDrawn = true
          }
        },
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .then(if (running) Modifier.clearAndSetSemantics {} else Modifier)
            .drawWithContent {
              drawContent()
              if (contentReady && !contentDrawn && size.width > 0 && size.height > 0) {
                contentDrawn = true
              }
            },
        propagateMinConstraints = true,
      ) {
        content()
      }
      if (running) {
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
        val drawing =
          remember(painter, colors, wordmark) {
            BoatLaunchDrawing(painter = painter, colors = colors, wordmark = wordmark)
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
          with(drawing) { draw(progress.value) }
        }
      }
    }
  }
}
