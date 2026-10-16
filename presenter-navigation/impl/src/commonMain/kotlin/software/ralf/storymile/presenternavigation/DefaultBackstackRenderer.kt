package software.ralf.storymile.presenternavigation

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.PresenterBackstackRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope

/**
 * Renders the active entry from [DefaultBackstackModel].
 *
 * Entry rendering delegates each [BaseModel] to its renderer through [Render].
 */
@ContributesRenderer
class DefaultBackstackRenderer : PresenterBackstackRenderer<DefaultBackstackModel>() {
  @Composable
  override fun PresenterNavDisplay(
    backstack: List<Int>,
    onBack: () -> Unit,
    entryProvider: (Int) -> NavEntry<Int>,
    modifier: Modifier,
  ) {
    val distance = with(LocalDensity.current) { 30.dp.roundToPx() }
    val forwardOffset =
      if (LocalLayoutDirection.current == LayoutDirection.Ltr) distance else -distance
    NavDisplay(
      backStack = backstack,
      onBack = onBack,
      entryProvider = entryProvider,
      modifier = modifier.clipToBounds(),
      transitionSpec = { sharedAxisTransition(forwardOffset) },
      popTransitionSpec = { sharedAxisTransition(-forwardOffset) },
      predictivePopTransitionSpec = { sharedAxisTransition(-forwardOffset) },
    )
  }

  @Composable
  override fun ComposeBackstackEntry(model: BaseModel) {
    // NavDisplay is implemented with AnimatedContent. Forward its entry-specific scope so child
    // renderers can coordinate shared elements with the outgoing or incoming navigation entry.
    CompositionLocalProvider(
      LocalAnimatedVisibilityScope provides LocalNavAnimatedContentScope.current,
    ) {
      Render(model)
    }
  }

  private fun sharedAxisTransition(offset: Int): ContentTransform {
    return (slideInHorizontally(tween(DURATION_MILLIS, easing = emphasized)) { offset } +
      fadeIn(tween(DURATION_MILLIS, easing = fadeInEasing))) togetherWith
      (slideOutHorizontally(tween(DURATION_MILLIS, easing = emphasized)) { -offset } +
        fadeOut(tween(DURATION_MILLIS, easing = fadeOutEasing)))
  }

  private companion object {
    // MaterialSharedAxis X: motionDurationLong1, emphasized easing, and a 35% fade threshold.
    // https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md
    const val DURATION_MILLIS = 450
    const val FADE_THRESHOLD = 0.35f
    val emphasized =
      PathEasing(
        Path().apply {
          moveTo(0f, 0f)
          cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
          cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
      )
    val fadeInEasing = Easing {
      ((emphasized.transform(it) - FADE_THRESHOLD) / (1f - FADE_THRESHOLD)).coerceIn(0f, 1f)
    }
    val fadeOutEasing = Easing { (emphasized.transform(it) / FADE_THRESHOLD).coerceIn(0f, 1f) }
  }
}
