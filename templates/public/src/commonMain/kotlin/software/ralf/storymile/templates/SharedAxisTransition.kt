package software.ralf.storymile.templates

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Material shared-axis motion for horizontal backstack navigation. */
object SharedAxisTransition {
  // MaterialSharedAxis X: motionDurationLong1, emphasized easing, and a 35% fade threshold.
  // https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md
  private const val DURATION_MILLIS = 450
  private const val FADE_THRESHOLD = 0.35f
  private val emphasized =
    PathEasing(
      Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
      },
    )
  private val fadeInEasing = Easing {
    ((emphasized.transform(it) - FADE_THRESHOLD) / (1f - FADE_THRESHOLD)).coerceIn(0f, 1f)
  }
  private val fadeOutEasing = Easing {
    (emphasized.transform(it) / FADE_THRESHOLD).coerceIn(0f, 1f)
  }

  /** Returns the forward travel distance in pixels for the current layout direction. */
  @Composable
  fun forwardOffset(): Int {
    val distance = with(LocalDensity.current) { 30.dp.roundToPx() }
    return if (LocalLayoutDirection.current == LayoutDirection.Ltr) distance else -distance
  }

  /** Slides and fades entries along the horizontal axis. Negate [offset] for a pop. */
  fun transition(offset: Int): ContentTransform {
    return (slideInHorizontally(tween(DURATION_MILLIS, easing = emphasized)) { offset } +
      fadeIn(tween(DURATION_MILLIS, easing = fadeInEasing))) togetherWith
      (slideOutHorizontally(tween(DURATION_MILLIS, easing = emphasized)) { -offset } +
        fadeOut(tween(DURATION_MILLIS, easing = fadeOutEasing)))
  }
}
