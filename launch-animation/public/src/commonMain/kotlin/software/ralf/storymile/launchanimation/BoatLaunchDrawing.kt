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
import androidx.compose.ui.graphics.lerp
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
  private val initialPainter: Painter?,
  private val initialColors: ColorScheme,
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
  private val initialGlow = initialColors.backdropGlow
  private val glow = colors.backdropGlow
  private var backdropBrushes: BackdropBrushes? = null

  fun DrawScope.draw(progress: Float, themeProgress: Float) {
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
    val coverAlpha = 1f - progress.segment(start = 0.64f, end = 0.91f)

    aperture.reset()
    aperture.addPath(outline)
    transform.reset()
    transform.translate(x = center.x - revealSize / 2f, y = center.y - revealSize / 2f)
    transform.scale(x = revealSize / 48f, y = revealSize / 48f)
    aperture.transform(transform)
    if (logoAlpha > 0f) {
      backdrop(alpha = logoAlpha, themeProgress = themeProgress)
    }
    // One continuous cover prevents seams between adjacent antialiased clips before the reveal.
    if (logoAlpha < 1f) {
      clipPath(path = aperture, clipOp = ClipOp.Difference) {
        backdrop(alpha = coverAlpha, themeProgress = themeProgress)
      }
    }
    if (logoAlpha > 0f) {
      val settle = (progress / 0.32f).coerceIn(0f, 1f)
      val decay = (1f - settle).pow(2)
      val angle = -3.2f * cos(PI.toFloat() * 2.1f * settle) * decay
      val drift = 4.dp.toPx() * sin(PI.toFloat() * 2f * settle) * decay
      rotate(degrees = angle, pivot = center) {
        translate(left = center.x - revealSize / 2f, top = center.y - revealSize / 2f + drift) {
          drawLogo(
            size = Size(width = revealSize, height = revealSize),
            alpha = logoAlpha,
            themeProgress = themeProgress,
          )
        }
      }
    }
    if (logoAlpha > 0f) {
      drawText(
        textLayoutResult = wordmark,
        color =
          color(
            initial = initialColors.onBackground,
            target = colors.onBackground,
            themeProgress = themeProgress,
          ),
        topLeft = Offset(x = center.x - wordmark.size.width / 2f, y = center.y + 61.dp.toPx()),
        alpha = logoAlpha,
      )
      val wakeWidth = 82.dp.toPx() * (1f + progress.segment(start = 0f, end = 0.32f) * 0.45f)
      drawOval(
        color =
          color(
            initial = initialColors.primary,
            target = colors.primary,
            themeProgress = themeProgress,
          ),
        topLeft = Offset(x = center.x - wakeWidth / 2f, y = center.y + 32.dp.toPx()),
        size = Size(width = wakeWidth, height = 10.dp.toPx()),
        alpha = logoAlpha * 0.11f,
        style = Stroke(1.dp.toPx()),
      )
    }
  }

  private fun DrawScope.drawLogo(size: Size, alpha: Float, themeProgress: Float) {
    if (initialPainter != null && themeProgress < 1f) {
      with(initialPainter) { draw(size = size, alpha = alpha) }
    }
    if (themeProgress > 0f) {
      with(painter) { draw(size = size, alpha = alpha * themeProgress) }
    }
  }

  private fun DrawScope.backdrop(alpha: Float, themeProgress: Float) {
    drawRect(
      color =
        color(
          initial = initialColors.background,
          target = colors.background,
          themeProgress = themeProgress,
        ),
      alpha = alpha,
    )
    val primaryContainer =
      color(
        initial = initialColors.primaryContainer,
        target = colors.primaryContainer,
        themeProgress = themeProgress,
      )
    val blendedGlow = color(initial = initialGlow, target = glow, themeProgress = themeProgress)
    val brushes =
      backdropBrushes
        ?.takeIf {
          it.size == size && it.primaryContainer == primaryContainer && it.glow == blendedGlow
        }
        ?.values ?: createBackdropBrushes(primaryContainer = primaryContainer, glow = blendedGlow)
    brushes.forEach { drawRect(brush = it, alpha = alpha) }
  }

  private fun DrawScope.createBackdropBrushes(primaryContainer: Color, glow: Color): List<Brush> {
    return listOf(
        Brush.radialGradient(
          colors = listOf(primaryContainer, Color.Transparent),
          center = Offset(x = size.width * 0.08f, y = size.height * 0.84f),
          radius = size.maxDimension * 0.76f,
        ),
        Brush.radialGradient(
          colors = listOf(glow, Color.Transparent),
          center = Offset(x = size.width * 0.8f, y = size.height * 0.12f),
          radius = size.maxDimension * 0.64f,
        ),
      )
      .also {
        backdropBrushes =
          BackdropBrushes(
            size = size,
            primaryContainer = primaryContainer,
            glow = glow,
            values = it,
          )
      }
  }

  private val ColorScheme.backdropGlow
    get() = if (background.luminance() < 0.5f) surfaceVariant else surface

  private fun color(initial: Color, target: Color, themeProgress: Float): Color =
    when (themeProgress) {
      0f -> initial
      1f -> target
      else -> lerp(start = initial, stop = target, fraction = themeProgress)
    }

  private fun Float.segment(start: Float, end: Float): Float {
    val value = ((this - start) / (end - start)).coerceIn(0f, 1f)
    return value * value * (3f - 2f * value)
  }

  private data class BackdropBrushes(
    val size: Size,
    val primaryContainer: Color,
    val glow: Color,
    val values: List<Brush>,
  )
}
