package software.ralf.storymile.launchanimation

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

internal class BoatLaunchDrawing(
  private val painter: Painter,
  private val colors: ColorScheme,
  private val wordmark: TextLayoutResult,
) {
  // The rounded silhouette from images/icon-light.svg and icon-dark.svg, in their 48-unit viewport.
  private val outline =
    PathParser()
      .parsePathString(
        "M6.31446 18.51099 Q6 18 6.58571 18.13016 L18.54808 20.78846 " +
          "L23.65592 13.49154 Q24 13 24.34408 13.49154 L29.45192 20.78846 " +
          "L41.41429 18.13016 Q42 18 41.68554 18.51099 L34.20964 30.65934 " +
          "Q34 31 33.60777 31.07845 L24.49029 32.90194 Q24 33 23.50971 32.90194 " +
          "L14.39223 31.07845 Q14 31 13.79036 30.65934Z",
      )
      .toPath()
  private val aperture = Path()
  private val transform = Matrix()
  private var backdropBrushes: Pair<Size, List<Brush>>? = null

  fun DrawScope.draw(progress: Float) {
    if (size.minDimension <= 0f) {
      return
    }
    val boatSize = minOf(156.dp.toPx(), size.minDimension * 0.48f)
    val center = Offset(x = size.width / 2f, y = size.height * 0.46f)
    val preparation = progress.segment(start = 0.19f, end = 0.34f)
    val zoom = progress.segment(start = 0.36f, end = 0.91f)
    val revealSize =
      boatSize *
        (1f - 0.09f * preparation) *
        exp(ln(size.maxDimension * 9f / (boatSize * 0.91f)) * zoom)
    val logoAlpha = 1f - progress.segment(start = 0.34f, end = 0.46f)
    val titleAlpha = 1f - progress.segment(start = 0.34f, end = 0.46f)

    aperture.reset()
    aperture.addPath(outline)
    transform.reset()
    transform.translate(x = center.x - revealSize / 2f, y = center.y - revealSize / 2f)
    transform.scale(x = revealSize / 48f, y = revealSize / 48f)
    aperture.transform(transform)
    if (logoAlpha > 0f) {
      backdrop(logoAlpha)
    }
    // One continuous cover prevents seams between adjacent antialiased clips before the reveal.
    if (logoAlpha < 1f) {
      clipPath(path = aperture, clipOp = ClipOp.Difference) { backdrop() }
    }
    if (logoAlpha > 0f) {
      val settle = (progress / 0.32f).coerceIn(0f, 1f)
      val decay = (1f - settle).pow(2)
      val angle = -3.2f * cos(PI.toFloat() * 2.1f * settle) * decay
      val drift = 4.dp.toPx() * sin(PI.toFloat() * 2f * settle) * decay
      rotate(degrees = angle, pivot = center) {
        translate(left = center.x - revealSize / 2f, top = center.y - revealSize / 2f + drift) {
          with(painter) {
            draw(size = Size(width = revealSize, height = revealSize), alpha = logoAlpha)
          }
        }
      }
    }
    if (titleAlpha > 0f) {
      drawText(
        textLayoutResult = wordmark,
        color = colors.onBackground,
        topLeft = Offset(x = center.x - wordmark.size.width / 2f, y = center.y + 61.dp.toPx()),
        alpha = titleAlpha,
      )
      val wakeWidth = 82.dp.toPx() * (1f + progress.segment(start = 0f, end = 0.32f) * 0.45f)
      drawOval(
        color = colors.primary,
        topLeft = Offset(x = center.x - wakeWidth / 2f, y = center.y + 32.dp.toPx()),
        size = Size(width = wakeWidth, height = 10.dp.toPx()),
        alpha = titleAlpha * 0.11f,
        style = Stroke(1.dp.toPx()),
      )
    }
  }

  private fun DrawScope.backdrop(alpha: Float = 1f) {
    drawRect(color = colors.background, alpha = alpha)
    val brushes = backdropBrushes?.takeIf { it.first == size }?.second ?: createBackdropBrushes()
    brushes.forEach { drawRect(brush = it, alpha = alpha) }
  }

  private fun DrawScope.createBackdropBrushes(): List<Brush> {
    val glow = if (colors.background.luminance() < 0.5f) colors.surfaceVariant else colors.surface
    return listOf(
        Brush.radialGradient(
          colors = listOf(colors.primaryContainer, Color.Transparent),
          center = Offset(x = size.width * 0.08f, y = size.height * 0.84f),
          radius = size.maxDimension * 0.76f,
        ),
        Brush.radialGradient(
          colors = listOf(glow, Color.Transparent),
          center = Offset(x = size.width * 0.8f, y = size.height * 0.12f),
          radius = size.maxDimension * 0.64f,
        ),
      )
      .also { backdropBrushes = size to it }
  }

  private fun Float.segment(start: Float, end: Float): Float {
    val value = ((this - start) / (end - start)).coerceIn(0f, 1f)
    return value * value * (3f - 2f * value)
  }
}
