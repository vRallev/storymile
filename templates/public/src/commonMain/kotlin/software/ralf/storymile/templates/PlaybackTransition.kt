package software.ralf.storymile.templates

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.lerp as lerpTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.ceil

/** Animates playback content with the sheet's [progress], from collapsed (0) to expanded (1). */
@Stable
class PlaybackTransition(private val progress: () -> Float) {
  private val elements = mutableMapOf<Any, Element>()

  /** Current sheet expansion, clamped to its two endpoints. Read during drawing or placement. */
  val fraction: Float
    get() = progress().coerceIn(0f, 1f)

  /**
   * Fades content that exists in only one playback state. Keeps its measured space and removes
   * hidden content from placement, input, and accessibility. Starts fading in after [startFraction]
   * of progress toward that state; the value must be in `[0, 1)`.
   */
  fun Modifier.fade(expanded: Boolean, startFraction: Float = 0f): Modifier {
    require(startFraction >= 0f && startFraction < 1f)
    fun opacity(): Float {
      val progress = if (expanded) fraction else 1f - fraction
      return ((progress - startFraction) / (1f - startFraction)).coerceIn(0f, 1f)
    }
    return layout { measurable, constraints ->
      val placeable = measurable.measure(constraints)
      layout(placeable.width, placeable.height) {
        if (opacity() > 0f) {
          placeable.placeRelative(0, 0)
        }
      }
    }
      .graphicsLayer { alpha = opacity() }
  }

  /**
   * Matches content with the same [key] in the two playback slots. Measures both endpoints and
   * interpolates their bounds and [cornerSize] with sheet movement. Moving content draws in the
   * shared transition overlay, outside parent fades and scroll clips.
   *
   * Draws one copy and scales each dimension to match the endpoint bounds. For text, use
   * [SharedText] to interpolate typography without stretching or duplicate glyphs. Higher
   * [zIndexInOverlay] values draw above other shared content during motion.
   *
   * Keep both endpoint containers measured. Apply [fade] to other content, outside this element.
   */
  @Composable
  fun SharedElement(
    key: Any,
    expanded: Boolean,
    cornerSize: CornerSize,
    modifier: Modifier = Modifier,
    zIndexInOverlay: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
  ) {
    SharedContent(key, expanded, cornerSize, modifier, zIndexInOverlay = zIndexInOverlay) {
      content()
    }
  }

  /**
   * Moves matching [key] text between playback slots. Keeps [content] for native layout and
   * semantics, and draws one [text] rendering during motion. Interpolates [style], available width,
   * and clipping height, preserving wrapping and ellipsis up to [maxLines].
   *
   * Supply the same text and style used by [content], including its color and a positive font size.
   * The [modifier] applies to the measured endpoint container. Both containers must remain measured
   * during motion. Fully scrolled-out endpoints fade to or from the visible endpoint.
   *
   * [horizontalArc] and [verticalArc] bend the path away from the straight line. Positive values
   * move right and down. [arcEasing] controls when the detour reaches its peak. Both arcs are zero
   * at the endpoints.
   */
  @Composable
  fun SharedText(
    key: Any,
    expanded: Boolean,
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    verticalArc: Dp = 0.dp,
    horizontalArc: Dp = 0.dp,
    arcEasing: Easing = LinearEasing,
    content: @Composable BoxScope.() -> Unit,
  ) {
    require(style.fontSize.value.isFinite() && style.fontSize.value > 0f)
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    SharedContent(
      key,
      expanded,
      CornerSize(0.dp),
      modifier,
      text = TextEndpoint(text, style, maxLines),
      verticalArc = verticalArc,
      horizontalArc = horizontalArc,
      arcEasing = arcEasing,
    ) { element ->
      Box(
        Modifier.drawWithContent {
          if (expanded && fraction > 0f && fraction < 1f) {
            element.drawText(this, textMeasurer, fraction)
          } else {
            drawContent()
          }
        },
        propagateMinConstraints = true,
        content = content,
      )
    }
  }

  @Composable
  private fun SharedContent(
    key: Any,
    expanded: Boolean,
    cornerSize: CornerSize,
    modifier: Modifier,
    text: TextEndpoint? = null,
    verticalArc: Dp = 0.dp,
    horizontalArc: Dp = 0.dp,
    arcEasing: Easing = LinearEasing,
    zIndexInOverlay: Float = 0f,
    content: @Composable BoxScope.(Element) -> Unit,
  ) {
    val element = remember(this, key) { elements.getOrPut(key) { Element() } }
    val owner = remember { Any() }
    val density = LocalDensity.current
    val overlayScope = LocalSharedTransitionScope.current
    val moving by remember(this) { derivedStateOf { fraction > 0f && fraction < 1f } }
    DisposableEffect(element, expanded, owner) {
      onDispose { element.remove(expanded, owner) }
    }
    val placed by
      remember(this, element, expanded) {
        derivedStateOf { if (expanded) fraction > 0f else element.visible(false, fraction) }
      }
    val accessible by
      remember(this, element, expanded) {
        derivedStateOf { element.visible(expanded, fraction) }
      }
    // Re-enter placement at a visibility boundary, even after an ancestor stopped placing us.
    val showContent = placed
    Box(
      modifier.onGloballyPositioned { coordinates ->
        val bounds =
          coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        element.update(
          expanded,
          Endpoint(
            bounds,
            cornerSize.toPx(Size(bounds.width, bounds.height), density),
            text,
            owner,
          ),
          fraction,
        ) {
          !coordinates.findRootCoordinates().localBoundingBoxOf(coordinates).isEmpty
        }
      },
      propagateMinConstraints = true,
    ) {
      Box(
        Modifier.then(
            if (overlayScope == null || !moving) {
              Modifier
            } else {
              with(overlayScope) {
                Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = zIndexInOverlay) {
                  fraction > 0f && fraction < 1f
                }
              }
            },
          )
          .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
              if (showContent) {
                placeable.placeRelative(0, 0)
              }
            }
          }
          .graphicsLayer {
            element.transform(
              this,
              expanded,
              fraction,
              cornerSize,
              verticalArc,
              horizontalArc,
              arcEasing,
            )
          }
          .then(if (accessible) Modifier else Modifier.clearAndSetSemantics {}),
        propagateMinConstraints = true,
      ) {
        content(element)
      }
    }
  }

  private class Element {
    private var collapsed: Endpoint? by mutableStateOf(null)
    private var expanded: Endpoint? by mutableStateOf(null)

    private val ready: Boolean
      get() = collapsed?.bounds?.isEmpty == false && expanded?.bounds?.isEmpty == false

    private val uniformScale: Boolean
      get() = collapsed?.contentScale != null && expanded?.contentScale != null

    fun visible(isExpanded: Boolean, fraction: Float): Boolean =
      when (fraction) {
        0f -> !isExpanded
        1f -> isExpanded
        else -> if (ready) isExpanded else !isExpanded
      }

    fun update(
      isExpanded: Boolean,
      endpoint: Endpoint,
      fraction: Float,
      visibleAtRest: () -> Boolean,
    ) {
      val previous = if (isExpanded) expanded else collapsed
      val atRest = if (isExpanded) fraction == 1f else fraction == 0f
      val updated =
        endpoint.copy(
          visibleAtRest = if (atRest) visibleAtRest() else previous?.visibleAtRest ?: true,
        )
      if (isExpanded) {
        expanded = updated
      } else {
        collapsed = updated
      }
    }

    fun remove(isExpanded: Boolean, owner: Any) {
      if (isExpanded && expanded?.owner === owner) {
        expanded = null
      } else if (!isExpanded && collapsed?.owner === owner) {
        collapsed = null
      }
    }

    fun transform(
      layer: GraphicsLayerScope,
      isExpanded: Boolean,
      fraction: Float,
      cornerSize: CornerSize,
      verticalArc: Dp,
      horizontalArc: Dp,
      arcEasing: Easing,
    ) =
      with(layer) {
        val start = collapsed
        val end = expanded
        alpha = alpha(isExpanded, fraction)
        transformOrigin = TransformOrigin(0f, 0f)
        if (start != null && end != null && ready && !size.isEmpty()) {
          val current = if (isExpanded) end else start
          if (uniformScale) {
            scaleX =
              lerp(requireNotNull(start.contentScale), requireNotNull(end.contentScale), fraction) /
                requireNotNull(current.contentScale)
            scaleY = scaleX
          } else {
            scaleX = lerp(start.bounds.width, end.bounds.width, fraction) / size.width
            scaleY = lerp(start.bounds.height, end.bounds.height, fraction) / size.height
          }
          val arcFraction = arcEasing.transform(fraction)
          val arc = 4f * arcFraction * (1f - arcFraction)
          translationX =
            lerp(start.bounds.left, end.bounds.left, fraction) - current.bounds.left +
              horizontalArc.toPx() * arc
          translationY =
            lerp(start.bounds.top, end.bounds.top, fraction) - current.bounds.top +
              verticalArc.toPx() * arc
          shape = RoundedCornerShape(lerp(start.cornerRadius, end.cornerRadius, fraction) / scaleX)
        } else {
          scaleX = 1f
          scaleY = 1f
          translationX = 0f
          translationY = 0f
          shape = RoundedCornerShape(cornerSize)
        }
        clip = !uniformScale
      }

    fun drawText(scope: ContentDrawScope, measurer: TextMeasurer, fraction: Float) {
      val start = collapsed
      val end = expanded
      val startText = start?.text
      val endText = end?.text
      if (startText == null || endText == null || !ready) {
        scope.drawContent()
        return
      }
      val style = lerpTextStyle(startText.style, endText.style, fraction)
      val scale = style.fontSize.value / endText.style.fontSize.value
      val width = lerp(start.bounds.width, end.bounds.width, fraction) / scale
      val height = lerp(start.bounds.height, end.bounds.height, fraction) / scale
      val layout =
        measurer.measure(
          text = endText.text,
          style = style.normalized(scale),
          overflow = TextOverflow.Ellipsis,
          maxLines = maxOf(startText.maxLines, endText.maxLines),
          constraints =
            Constraints(
              maxWidth = ceil(width).toInt().coerceAtLeast(1),
              maxHeight = ceil(height).toInt().coerceAtLeast(1),
            ),
        )
      with(scope) {
        clipRect(right = width, bottom = height) { drawText(layout) }
      }
    }

    private fun TextStyle.normalized(scale: Float): TextStyle =
      copy(
        fontSize = fontSize / scale,
        letterSpacing = if (letterSpacing.isSp) letterSpacing / scale else letterSpacing,
        lineHeight = if (lineHeight.isSp) lineHeight / scale else lineHeight,
      )

    private fun alpha(isExpanded: Boolean, fraction: Float): Float =
      if (!visible(isExpanded, fraction)) {
        0f
      } else if (!ready) {
        1f
      } else {
        lerp(requireNotNull(collapsed).alphaAtRest, requireNotNull(expanded).alphaAtRest, fraction)
      }
  }

  private data class Endpoint(
    val bounds: Rect,
    val cornerRadius: Float,
    val text: TextEndpoint?,
    val owner: Any,
    val visibleAtRest: Boolean = true,
  ) {
    val contentScale: Float?
      get() = text?.style?.fontSize?.value

    val alphaAtRest: Float
      get() = if (visibleAtRest) 1f else 0f
  }

  private data class TextEndpoint(val text: String, val style: TextStyle, val maxLines: Int)
}

/** Sheet animation shared by the collapsed and expanded playback renderers. */
val LocalPlaybackTransition = staticCompositionLocalOf<PlaybackTransition?> { null }
