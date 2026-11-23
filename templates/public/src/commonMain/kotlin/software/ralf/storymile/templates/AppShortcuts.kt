package software.ralf.storymile.templates

import androidx.compose.ui.input.key.Key

/** Supplies optional actions for hidden app shortcuts. */
interface AppShortcuts {
  /** Action for holding the logo, when available. */
  val logoLongPress: (suspend () -> Unit)?

  /** Returns the action for a Desktop command or control shortcut, when available. */
  fun actionForKey(key: Key): (suspend () -> Unit)?
}
