package software.ralf.storymile

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Window presets for exercising phone, tablet, and desktop layouts. */
object DesktopWindowSizes {
  /** Portrait preset that exercises the phone backstack presentation. */
  val phone = DpSize(width = 480.dp, height = 840.dp)

  /** Landscape preset that exercises the tablet two-pane presentation. */
  val tablet = DpSize(width = 1100.dp, height = 760.dp)

  /** Wide preset that exercises expanded navigation. */
  val desktop = DpSize(width = 1440.dp, height = 900.dp)

  /** Cycles phone, tablet, and desktop presets. Other sizes return to phone. */
  fun next(size: DpSize): DpSize {
    return when (size) {
      phone -> tablet
      tablet -> desktop
      else -> phone
    }
  }
}
