package software.ralf.storymile.templates

import androidx.compose.runtime.compositionLocalOf

/** Placement of app tabs selected by the template renderer. */
enum class TabPlacement {
  /** Render tabs in a horizontal bar below playback. */
  BOTTOM,

  /** Render tabs in a narrow vertical rail beside the content. */
  START,

  /** Render tabs in a wide sidebar beside the content. */
  START_EXPANDED,
}

/** Lets tabs adapt their layout without changing their presenter model. */
val LocalTabPlacement = compositionLocalOf { TabPlacement.BOTTOM }
