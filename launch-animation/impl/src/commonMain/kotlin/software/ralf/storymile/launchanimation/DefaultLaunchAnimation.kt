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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.design.storymileLogoPainter
import software.ralf.storymile.launch.animation.impl.generated.resources.Res
import software.ralf.storymile.launch.animation.impl.generated.resources.launch_description
import software.ralf.storymile.launch.animation.impl.generated.resources.launch_title
import software.ralf.storymile.theme.AppTheme

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLaunchAnimation : LaunchAnimation {
  private var started = false

  @Composable
  override fun Content(modifier: Modifier, content: @Composable () -> Unit) {
    val progress =
      remember(this) {
        val initial = if (started) 1f else 0f
        // Claim the cold launch before another host can enter composition in the same app scope.
        started = true
        Animatable(initial)
      }
    val running by remember(progress) { derivedStateOf { progress.value < 1f } }
    LaunchedEffect(progress) {
      if (progress.value < 1f) {
        // Compose applies the platform's animation duration scale, including disabled animations.
        progress.animateTo(targetValue = 1f, animationSpec = tween(1200, easing = LinearEasing))
      }
    }

    Box(modifier.onPreviewKeyEvent { running }) {
      Box(
        modifier =
          Modifier.fillMaxSize().then(if (running) Modifier.clearAndSetSemantics {} else Modifier),
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
