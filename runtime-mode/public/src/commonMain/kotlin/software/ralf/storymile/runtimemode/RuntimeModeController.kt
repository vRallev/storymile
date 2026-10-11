package software.ralf.storymile.runtimemode

/** Stores the app-wide mode and restarts the application when it changes. */
interface RuntimeModeController {
  /**
   * Waits for the saved mode and caches it until the app restarts. Uses [RuntimeMode.DEFAULT] only
   * when no mode is saved. Storage failures and unknown saved values throw instead of returning a
   * default.
   */
  suspend fun mode(): RuntimeMode

  /**
   * Saves [mode] for the next app start, then requests an application restart. Selecting the saved
   * mode does not restart, including while the initial value is loading. Storage failures propagate
   * to the caller without requesting a restart.
   */
  suspend fun switchMode(mode: RuntimeMode)
}
