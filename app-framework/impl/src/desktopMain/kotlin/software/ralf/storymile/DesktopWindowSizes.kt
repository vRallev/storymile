package software.ralf.storymile

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import software.ralf.storymile.screen.ScreenSize

/** Useful window presets for exercising compact and two-pane layouts. */
object DesktopWindowSizes {
  /** Portrait preset that exercises the phone backstack presentation. */
  val phone = DpSize(width = 480.dp, height = 840.dp)

  /** Landscape preset that exercises the tablet two-pane presentation. */
  val tablet = DpSize(width = 1100.dp, height = 760.dp)

  /** Returns whether [size] belongs to either shared tablet category. */
  fun isTablet(size: DpSize): Boolean {
    return ScreenSize.from(size.width, size.height).category != ScreenSize.Category.PHONE
  }
}
