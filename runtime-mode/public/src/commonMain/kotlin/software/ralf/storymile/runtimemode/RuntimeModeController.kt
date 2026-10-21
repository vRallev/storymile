package software.ralf.storymile.runtimemode

/** Stores the app-wide mode and restarts the application when it changes. */
interface RuntimeModeController {
  /**
   * Loads the saved mode on first access and caches it until the app restarts. The first read
   * blocks on Android, Desktop, and iOS. Requires an active app scope. Uses [RuntimeMode.Real] only
   * when no mode is saved. Storage failures and unknown saved values throw instead of returning a
   * default.
   */
  val mode: RuntimeMode

  /**
   * Saves [mode] for the next app start, then requests an application restart. Selecting the saved
   * mode does not restart. Storage failures propagate to the caller without requesting a restart.
   */
  suspend fun switchMode(mode: RuntimeMode)
}
