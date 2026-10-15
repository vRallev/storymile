package software.ralf.storymile.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shares a controller across UI hosts until the process exits, unless a local instance is supplied.
 */
val LocalThemeController = staticCompositionLocalOf { ThemeController.processInstance }

/** Holds a manual appearance choice in memory without saving it to storage. */
class ThemeController {
  /** The manual appearance, or null until the first toggle. */
  var darkThemeOverride: Boolean? by mutableStateOf(null)
    private set

  /** Toggles the manual appearance, using [darkTheme] when no choice has been made. */
  fun toggle(darkTheme: Boolean) {
    darkThemeOverride = !(darkThemeOverride ?: darkTheme)
  }

  internal companion object {
    val processInstance = ThemeController()
  }
}
