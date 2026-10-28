package software.ralf.storymile.shows

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
import androidx.compose.ui.platform.testTag

@Composable
internal fun FollowIcon(followed: Boolean, modifier: Modifier = Modifier) {
  val progress =
    animateFloatAsState(
      targetValue = if (followed) 1f else 0f,
      animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
      label = "Follow icon morph",
    )
  Icon(
    painter = remember { FollowIconPainter(progress) },
    contentDescription = null,
    modifier = modifier.testTag("show-follow-icon"),
  )
}

private class FollowIconPainter(private val progress: State<Float>) : Painter() {
  // Let Material Icon apply its default size in dp.
  override val intrinsicSize = Size.Unspecified
  private val path = Path()

  // Matching clockwise quadrilaterals retain the Material Add and Check silhouettes.
  private val plus =
    listOf(
      Offset(x = 5f, y = 11f),
      Offset(x = 19f, y = 11f),
      Offset(x = 19f, y = 13f),
      Offset(x = 5f, y = 13f),
      Offset(x = 11f, y = 5f),
      Offset(x = 13f, y = 5f),
      Offset(x = 13f, y = 19f),
      Offset(x = 11f, y = 19f),
    )
  private val check =
    listOf(
      Offset(x = 4.83f, y = 12f),
      Offset(x = 9f, y = 16.17f),
      Offset(x = 9f, y = 19f),
      Offset(x = 3.41f, y = 13.41f),
      Offset(x = 19.59f, y = 5.59f),
      Offset(x = 21f, y = 7f),
      Offset(x = 9f, y = 19f),
      Offset(x = 9f, y = 16.17f),
    )

  override fun DrawScope.onDraw() {
    path.reset()
    repeat(2) { contour ->
      val first = contour * 4
      repeat(4) { vertex ->
        val index = first + vertex
        val point = lerp(start = plus[index], stop = check[index], fraction = progress.value)
        if (vertex == 0) {
          path.moveTo(x = point.x, y = point.y)
        } else {
          path.lineTo(x = point.x, y = point.y)
        }
      }
      path.close()
    }
    scale(scaleX = size.width / 24f, scaleY = size.height / 24f, pivot = Offset.Zero) {
      drawPath(path = path, color = Color.Black)
    }
  }
}
