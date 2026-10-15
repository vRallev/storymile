package software.ralf.storymile.design

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Invokes [onLongPress] once after a five-second hold without visible feedback. */
@Composable
fun Modifier.hiddenLongPress(onLongPress: () -> Unit): Modifier {
  val currentOnLongPress by rememberUpdatedState(onLongPress)
  return pointerInput(Unit) {
    awaitEachGesture {
      awaitFirstDown().consume()
      val releasedOrCancelled =
        withTimeoutOrNull(5_000L) {
          waitForUpOrCancellation()
          true
        } ?: false
      if (!releasedOrCancelled) {
        currentOnLongPress()
      }
    }
  }
}
