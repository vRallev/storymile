package software.ralf.storymile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/** Supplies predictive back events from mouse drags at either window edge. */
@Composable
internal fun DesktopBackGesture(content: @Composable () -> Unit) {
  val dispatcher =
    checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
  val input = remember(dispatcher) { DesktopBackGestureInput() }
  val focused = LocalWindowInfo.current.isWindowFocused
  var size by remember { mutableStateOf(IntSize.Zero) }

  DisposableEffect(dispatcher, input) {
    dispatcher.addInput(input)
    onDispose {
      input.cancel()
      dispatcher.removeInput(input)
    }
  }

  Box(
    Modifier.fillMaxSize()
      .onSizeChanged { size = it }
      .onPreviewKeyEvent {
        it.key == Key.Escape && it.type == KeyEventType.KeyDown && input.cancel()
      }
      .pointerInput(input, focused, size) {
        if (focused) {
          input.detectGestures(this)
        }
      },
    propagateMinConstraints = true,
  ) {
    content()
  }
}
