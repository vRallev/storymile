package software.ralf.storymile.theme

import kotlinx.coroutines.flow.StateFlow

/** Shares the appearance preference across the app's UI hosts. */
interface ThemeController {
  /** The selected appearance. The default follows the system. */
  val mode: StateFlow<ThemeMode>

  /** Waits until [mode] reflects the saved preference or the system default. */
  suspend fun awaitInitialMode()

  /** Applies [mode] to the app and saves it for future launches. */
  fun setMode(mode: ThemeMode)

  /** Selects the opposite appearance, using [systemDarkTheme] when following the system. */
  fun toggle(systemDarkTheme: Boolean)
}
