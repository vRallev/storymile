package software.ralf.storymile

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import kotlin.math.abs

internal class DesktopBackGestureInput : NavigationEventInput() {
  private var enabled = false
  private var active = false

  override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
    enabled = hasEnabledHandlers
  }

  suspend fun detectGestures(scope: PointerInputScope) {
    with(scope) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabled || down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) {
          return@awaitEachGesture
        }
        val edgeWidth = 24.dp.toPx()
        val edge =
          when {
            down.position.x <= edgeWidth -> NavigationEvent.EDGE_LEFT
            down.position.x >= size.width - edgeWidth -> NavigationEvent.EDGE_RIGHT
            else -> return@awaitEachGesture
          }
        val direction = if (edge == NavigationEvent.EDGE_LEFT) 1f else -1f
        val change = awaitInwardDrag(down, direction) ?: return@awaitEachGesture
        if (!enabled) {
          return@awaitEachGesture
        }
        val previewDistance = minOf(300.dp.toPx(), size.width / 2f).coerceAtLeast(1f)
        active = true
        try {
          dispatchOnBackStarted(down.navigationEvent(edge, 0f))
          trackGesture(down, change, edge, direction, previewDistance)
        } finally {
          // Focus, size, or composition changes must not leave a preview active.
          cancel()
        }
      }
    }
  }

  private suspend fun AwaitPointerEventScope.awaitInwardDrag(
    down: PointerInputChange,
    direction: Float,
  ): PointerInputChange? {
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val change = event.changes.firstOrNull { it.id == down.id } ?: return null
      val delta = change.position - down.position
      val distance = delta.x * direction
      // Leave clicks, vertical scrolling, and outward drags to the content.
      if (
        change.isConsumed ||
          !event.buttons.isPrimaryPressed ||
          (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) ||
          distance < -viewConfiguration.touchSlop
      ) {
        return null
      }
      if (distance > viewConfiguration.touchSlop) {
        return change
      }
    }
  }

  private suspend fun AwaitPointerEventScope.trackGesture(
    down: PointerInputChange,
    firstChange: PointerInputChange,
    edge: Int,
    direction: Float,
    previewDistance: Float,
  ) {
    var change = firstChange
    while (active) {
      // Compose marks synthetic releases as consumed when the pointer target is removed.
      if (change.isConsumed) {
        return
      }
      // Claim the drag before child click/scroll handlers see it.
      change.consume()
      val progress =
        ((change.position.x - down.position.x) * direction / previewDistance).coerceIn(0f, 1f)
      dispatchOnBackProgressed(change.navigationEvent(edge, progress))
      if (!currentEvent.buttons.isPrimaryPressed) {
        finish(complete = progress >= 0.5f)
        return
      }
      change =
        awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
          ?: return
    }
  }

  fun cancel(): Boolean {
    if (!active) {
      return false
    }
    finish(complete = false)
    return true
  }

  private fun finish(complete: Boolean) {
    active = false
    if (complete) {
      dispatchOnBackCompleted()
    } else {
      dispatchOnBackCancelled()
    }
  }

  private fun PointerInputChange.navigationEvent(edge: Int, progress: Float): NavigationEvent =
    NavigationEvent(
      swipeEdge = edge,
      progress = progress,
      touchX = position.x.coerceAtLeast(0f),
      touchY = position.y.coerceAtLeast(0f),
      frameTimeMillis = uptimeMillis,
    )
}
