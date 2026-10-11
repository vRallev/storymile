package software.ralf.storymile.restart

/** Performs the platform's hard restart or termination after scope cleanup. */
fun interface ProcessRestarter {
  /** Restarts the process, reloads the page, or terminates the iOS app. */
  fun restart()
}
