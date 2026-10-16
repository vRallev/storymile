package software.ralf.storymile.discovery.presentation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun Modifier.discoveryScreenTransition(): Modifier {
  val visibilityScope = LocalAnimatedVisibilityScope.current
  val transition =
    if (visibilityScope == null) {
      this
    } else {
      with(visibilityScope) {
        animateEnterExit(
          enter = fadeIn(tween(250, delayMillis = 100)),
          exit = fadeOut(tween(100)),
        )
      }
    }
  return transition.background(AppTheme.colorScheme.background)
}
