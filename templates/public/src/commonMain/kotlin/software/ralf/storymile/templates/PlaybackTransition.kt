package software.ralf.storymile.templates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp

/** Animates playback content with the sheet's [progress], from collapsed (0) to expanded (1). */
@Stable
class PlaybackTransition(private val progress: () -> Float) {
  private val elements = mutableMapOf<Any, Element>()

  /** Current sheet expansion, clamped to its two endpoints. Read during drawing or placement. */
  val fraction: Float
    get() = progress().coerceIn(0f, 1f)

  /**
   * Fades content that exists in only one playback state. Keeps its measured space and removes
   * hidden content from placement, input, and accessibility.
   */
  fun Modifier.fade(expanded: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
      val alpha = if (expanded) fraction else 1f - fraction
      if (alpha > 0f) {
        placeable.placeRelative(0, 0)
      }
    }
  }
    .graphicsLayer { alpha = if (expanded) fraction else 1f - fraction }

  /**
   * Matches content with the same [key] in the two playback slots. Measures both endpoints and
   * draws one copy, interpolating its bounds and [cornerSize] with sheet movement.
   *
   * Keep both endpoint containers measured. Apply [fade] to other content, outside this element.
   */
  @Composable
  fun SharedElement(
    key: Any,
    expanded: Boolean,
    cornerSize: CornerSize,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
  ) {
    val element = remember(this, key) { elements.getOrPut(key) { Element() } }
    val density = LocalDensity.current
    val visible by
      remember(this, element, expanded) {
        derivedStateOf {
          if (expanded) fraction > 0f && element.ready else fraction == 0f || !element.ready
        }
      }
    Box(
      modifier.onGloballyPositioned { coordinates ->
        val bounds =
          coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        val endpoint = Endpoint(bounds, cornerSize.toPx(Size(bounds.width, bounds.height), density))
        if (expanded) {
          element.expanded = endpoint
        } else {
          element.collapsed = endpoint
        }
      },
    ) {
      Box(
        Modifier.matchParentSize()
          .graphicsLayer {
            val start = element.collapsed
            val end = element.expanded
            alpha = if (visible) 1f else 0f
            transformOrigin = TransformOrigin(0f, 0f)
            if (expanded && start != null && end != null && element.ready && !size.isEmpty()) {
              val progress = fraction
              scaleX = lerp(start.bounds.width, end.bounds.width, progress) / size.width
              scaleY = lerp(start.bounds.height, end.bounds.height, progress) / size.height
              translationX = (start.bounds.left - end.bounds.left) * (1f - progress)
              translationY = (start.bounds.top - end.bounds.top) * (1f - progress)
              shape =
                RoundedCornerShape(lerp(start.cornerRadius, end.cornerRadius, progress) / scaleX)
            } else {
              scaleX = 1f
              scaleY = 1f
              translationX = 0f
              translationY = 0f
              shape = RoundedCornerShape(cornerSize)
            }
            clip = true
          }
          .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
        content = content,
      )
    }
  }

  private class Element {
    var collapsed: Endpoint? by mutableStateOf(null)
    var expanded: Endpoint? by mutableStateOf(null)

    val ready: Boolean
      get() = collapsed?.bounds?.isEmpty == false && expanded?.bounds?.isEmpty == false
  }

  private data class Endpoint(val bounds: Rect, val cornerRadius: Float)
}

/** Sheet animation shared by the collapsed and expanded playback renderers. */
val LocalPlaybackTransition = staticCompositionLocalOf<PlaybackTransition?> { null }
