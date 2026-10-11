package software.ralf.storymile.library

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun LibraryIllustration(modifier: Modifier = Modifier) {
  val colors = AppTheme.colorScheme
  val dark = colors.background.luminance() < 0.5f
  val cover = if (dark) colors.primaryContainer else colors.primary
  val pageBlue = if (dark) colors.primary else colors.primaryContainer
  val pageWhite = if (dark) colors.primaryFixed else colors.surface
  val pageEdge = lerp(pageBlue, cover, 0.25f)
  val bookmark = if (dark) colors.primaryContainer else colors.primary

  Canvas(modifier) {
    val factor = minOf(size.width, size.height) / 280f
    withTransform(
      {
        translate((size.width - 280f * factor) / 2f, (size.height - 280f * factor) / 2f)
        scale(factor, factor, pivot = Offset.Zero)
      },
    ) {
      drawOval(
        color = colors.primary.copy(alpha = if (dark) 0.22f else 0.12f),
        topLeft = Offset(20f, 257f),
        size = Size(240f, 17f),
      )
      drawPath(
        path = BookPaths.cover,
        brush =
          Brush.linearGradient(
            colors = listOf(cover, lerp(cover, colors.primary, 0.22f)),
            start = Offset(20f, 95f),
            end = Offset(260f, 250f),
          ),
      )
      drawPath(BookPaths.leftPages, pageEdge)
      drawPath(BookPaths.rightPages, pageEdge)
      drawPath(
        path = BookPaths.leftPage,
        brush =
          Brush.linearGradient(
            colors = listOf(lerp(pageBlue, pageWhite, 0.3f), pageBlue),
            start = Offset(43f, 130f),
            end = Offset(140f, 130f),
          ),
      )
      drawPath(
        path = BookPaths.rightPage,
        brush =
          Brush.linearGradient(
            colors = listOf(pageWhite, lerp(pageWhite, colors.surface, 0.1f)),
            start = Offset(237f, 120f),
            end = Offset(140f, 245f),
          ),
      )
      drawPath(
        path = BookPaths.bookmark,
        brush =
          Brush.linearGradient(
            colors = listOf(bookmark, lerp(bookmark, colors.primary, 0.25f)),
            start = Offset(178f, 75f),
            end = Offset(213f, 150f),
          ),
      )
      drawLine(
        color = colors.primary,
        start = Offset(140f, 17f),
        end = Offset(140f, 47f),
        strokeWidth = 6f,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = colors.primary,
        start = Offset(96f, 36f),
        end = Offset(111f, 59f),
        strokeWidth = 6f,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = colors.primary,
        start = Offset(184f, 36f),
        end = Offset(169f, 59f),
        strokeWidth = 6f,
        cap = StrokeCap.Round,
      )
    }
  }
}

private object BookPaths {
  val cover =
    Path().apply {
      moveTo(28f, 100f)
      quadraticTo(20f, 100f, 20f, 109f)
      lineTo(20f, 241f)
      quadraticTo(20f, 248f, 28f, 249f)
      cubicTo(67f, 251f, 104f, 254f, 124f, 261f)
      cubicTo(132f, 266f, 148f, 266f, 156f, 261f)
      cubicTo(176f, 254f, 213f, 251f, 252f, 249f)
      quadraticTo(260f, 248f, 260f, 241f)
      lineTo(260f, 109f)
      quadraticTo(260f, 100f, 252f, 100f)
      cubicTo(207f, 101f, 163f, 112f, 140f, 128f)
      cubicTo(117f, 112f, 73f, 101f, 28f, 100f)
      close()
    }

  val leftPages =
    Path().apply {
      moveTo(37f, 90f)
      quadraticTo(29f, 90f, 29f, 98f)
      lineTo(29f, 230f)
      quadraticTo(29f, 238f, 37f, 238f)
      cubicTo(77f, 239f, 114f, 246f, 140f, 254f)
      lineTo(140f, 121f)
      cubicTo(109f, 99f, 75f, 90f, 37f, 90f)
      close()
    }

  val rightPages =
    Path().apply {
      moveTo(243f, 90f)
      quadraticTo(251f, 90f, 251f, 98f)
      lineTo(251f, 230f)
      quadraticTo(251f, 238f, 243f, 238f)
      cubicTo(203f, 239f, 166f, 246f, 140f, 254f)
      lineTo(140f, 121f)
      cubicTo(171f, 99f, 205f, 90f, 243f, 90f)
      close()
    }

  val leftPage =
    Path().apply {
      moveTo(49f, 78f)
      quadraticTo(43f, 77f, 43f, 84f)
      lineTo(43f, 212f)
      quadraticTo(43f, 220f, 51f, 221f)
      cubicTo(88f, 222f, 118f, 237f, 140f, 254f)
      lineTo(140f, 119f)
      cubicTo(122f, 98f, 86f, 84f, 49f, 78f)
      close()
    }

  val rightPage =
    Path().apply {
      moveTo(231f, 78f)
      quadraticTo(237f, 77f, 237f, 84f)
      lineTo(237f, 212f)
      quadraticTo(237f, 220f, 229f, 221f)
      cubicTo(192f, 222f, 162f, 237f, 140f, 254f)
      lineTo(140f, 119f)
      cubicTo(158f, 98f, 194f, 84f, 231f, 78f)
      close()
    }

  val bookmark =
    Path().apply {
      moveTo(178f, 94f)
      quadraticTo(194f, 85f, 212f, 82f)
      lineTo(212f, 156f)
      quadraticTo(212f, 160f, 208f, 158f)
      lineTo(195f, 151f)
      lineTo(182f, 163f)
      quadraticTo(178f, 166f, 178f, 162f)
      close()
    }
}
