package software.ralf.storymile.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.painterResource
import software.ralf.storymile.theme.generated.resources.Res
import software.ralf.storymile.theme.generated.resources.storymile_logo

/** Themed logo with a hidden five-second hold action supplied by [LocalOnLogoLongPress]. */
@Composable
fun StorymileLogo(modifier: Modifier = Modifier) {
  val onLongPress by rememberUpdatedState(LocalOnLogoLongPress.current)
  Image(
    painter = painterResource(Res.drawable.storymile_logo),
    contentDescription = null,
    modifier =
      modifier.pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown().consume()
          val releasedOrCancelled =
            withTimeoutOrNull(5_000L) {
              waitForUpOrCancellation()
              true
            } ?: false
          if (!releasedOrCancelled) {
            onLongPress()
          }
        }
      },
  )
}
