package software.ralf.storymile.podcastshows

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.theme.AppTheme

/** Fades the podcast screen background in after its shared elements start moving. */
@Composable
fun Modifier.podcastScreenTransition(): Modifier {
  val visibilityScope = LocalAnimatedVisibilityScope.current
  val transition =
    if (visibilityScope == null) {
      this
    } else {
      with(visibilityScope) {
        animateEnterExit(
          enter = fadeIn(tween(durationMillis = 100, delayMillis = 350)),
          exit = fadeOut(tween(100)),
        )
      }
    }
  return transition.background(AppTheme.colorScheme.background)
}
