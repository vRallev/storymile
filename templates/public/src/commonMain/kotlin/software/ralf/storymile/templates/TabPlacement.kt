package software.ralf.storymile.templates

import androidx.compose.runtime.compositionLocalOf

/** Placement of app tabs selected by the template renderer. */
enum class TabPlacement {
  /** Render tabs in a horizontal bar below playback. */
  BOTTOM,

  /** Render tabs in a vertical rail or sidebar beside the content. */
  START,
}

/** Lets tabs adapt their layout without changing their presenter model. */
val LocalTabPlacement = compositionLocalOf { TabPlacement.BOTTOM }
