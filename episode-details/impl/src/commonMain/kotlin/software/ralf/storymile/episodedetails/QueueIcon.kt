package software.ralf.storymile.episodedetails

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection

@Composable
internal fun QueueIcon(isQueued: Boolean, modifier: Modifier = Modifier) {
  val progress =
    animateFloatAsState(
      targetValue = if (isQueued) 1f else 0f,
      animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
      label = "Queue icon morph",
    )
  Icon(
    painter = remember { QueueIconPainter(progress) },
    contentDescription = null,
    modifier = modifier,
  )
}

private class QueueIconPainter(private val progress: State<Float>) : Painter() {
  override val intrinsicSize = Size.Unspecified
  private val path = Path()

  // Start at the top arm and trace clockwise. Plus x coordinates are relative to its center.
  private val plus =
    listOf(
      Offset(x = -1f, y = 10f),
      Offset(x = 1f, y = 10f),
      Offset(x = 1f, y = 14f),
      Offset(x = 5f, y = 14f),
      Offset(x = 5f, y = 16f),
      Offset(x = 1f, y = 16f),
      Offset(x = 1f, y = 20f),
      Offset(x = -1f, y = 20f),
      Offset(x = -1f, y = 16f),
      Offset(x = -5f, y = 16f),
      Offset(x = -5f, y = 14f),
      Offset(x = -1f, y = 14f),
    )

  // The top and left arms become the check strokes. The right and bottom arms fold into the edge.
  private val check =
    listOf(
      Offset(x = 19.59f, y = 5.59f),
      Offset(x = 21f, y = 7f),
      Offset(x = 17f, y = 11f),
      Offset(x = 15f, y = 13f),
      Offset(x = 15f, y = 13f),
      Offset(x = 13f, y = 15f),
      Offset(x = 9f, y = 19f),
      Offset(x = 9f, y = 19f),
      Offset(x = 7f, y = 17f),
      Offset(x = 3.41f, y = 13.41f),
      Offset(x = 4.83f, y = 12f),
      Offset(x = 9f, y = 16.17f),
    )

  override fun DrawScope.onDraw() {
    val fraction = progress.value
    val rtl = layoutDirection == LayoutDirection.Rtl
    val plusCenter = if (rtl) 7f else 17f
    path.reset()
    for (index in plus.indices) {
      val start = plus[index]
      val point =
        lerp(
          start = start.copy(x = start.x + plusCenter),
          stop = check[index],
          fraction = fraction,
        )
      if (index == 0) {
        path.moveTo(x = point.x, y = point.y)
      } else {
        path.lineTo(x = point.x, y = point.y)
      }
    }
    path.close()
    scale(scaleX = size.width / 24f, scaleY = size.height / 24f, pivot = Offset.Zero) {
      drawQueueLines(fraction = fraction, rtl = rtl)
      drawPath(path = path, color = Color.Black)
    }
  }

  private fun DrawScope.drawQueueLines(fraction: Float, rtl: Boolean) {
    repeat(3) { line ->
      val width = if (line == 2) 7f else 11f
      val retraction = (fraction - 0.07f * line) / 0.5f
      val remaining = 1f - retraction.coerceIn(minimumValue = 0f, maximumValue = 1f)
      val remainingWidth = width * remaining
      val left = if (rtl) 21f - width else 3f + width - remainingWidth
      drawRect(
        color = Color.Black,
        topLeft = Offset(x = left, y = 6f + 4f * line),
        size = Size(width = remainingWidth, height = 2f),
        alpha = remaining,
      )
    }
  }
}
