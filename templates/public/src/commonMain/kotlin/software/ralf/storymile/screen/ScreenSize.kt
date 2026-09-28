package software.ralf.storymile.screen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * Window dimensions, category, and orientation shared by renderers and presenters.
 *
 * Instances are created through [from] so category calculation remains consistent across all
 * platforms.
 */
@ConsistentCopyVisibility
data class ScreenSize
private constructor(
  /** Current window width in density-independent pixels. */
  val width: Dp,
  /** Current window height in density-independent pixels. */
  val height: Dp,
  /** Layout category selected from the shorter window dimension. */
  val category: Category,
  /** Orientation selected from the current window dimensions. */
  val orientation: Orientation,
) {
  /** Creates screen sizes from window dimensions. */
  companion object {
    /** Initial value used before a platform reports a specified window size. */
    val Zero = from(width = 0.dp, height = 0.dp)

    /**
     * Derives category from the shorter side: phone below 600 dp, small tablet below 840 dp, and
     * large tablet otherwise. Rotating the same window preserves its category.
     *
     * Square windows are portrait, including [Zero] before the first measurement.
     */
    fun from(width: Dp, height: Dp): ScreenSize {
      require(width.isSpecified) { "width must be specified." }
      require(height.isSpecified) { "height must be specified." }

      val shortSide = minOf(width, height)

      return ScreenSize(
        width = width,
        height = height,
        category =
          when {
            shortSide < 600.dp -> Category.PHONE
            shortSide < 840.dp -> Category.TABLET_SMALL
            else -> Category.TABLET_LARGE
          },
        orientation = if (width > height) Orientation.LANDSCAPE else Orientation.PORTRAIT,
      )
    }
  }

  /** Adaptive window categories, independent of rotation. */
  enum class Category {
    /** The shorter side is less than 600 dp. */
    PHONE,

    /** The shorter side is at least 600 dp and less than 840 dp. */
    TABLET_SMALL,

    /** The shorter side is at least 840 dp. */
    TABLET_LARGE,
  }

  /** Orientation of the app window, including freely resizable windows. */
  enum class Orientation {
    /** The window is taller than it is wide, or square. */
    PORTRAIT,

    /** The window is wider than it is tall. */
    LANDSCAPE,
  }
}
