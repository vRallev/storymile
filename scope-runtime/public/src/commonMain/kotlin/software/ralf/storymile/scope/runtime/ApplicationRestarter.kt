package software.ralf.storymile.scope.runtime

/** Restarts the application while preserving saved data. */
interface ApplicationRestarter {
  /**
   * Destroys the app scope and waits up to five seconds for coroutine cleanup before restarting.
   * Synchronous scope callbacks must return promptly so they do not delay the restart.
   *
   * Android and Desktop launch a new process. Web reloads the page. iOS closes the app without a
   * dialog; the user must open it again.
   *
   * Returns immediately and accepts calls from any thread, including an outgoing presenter.
   * Repeated requests are combined.
   */
  fun restart()
}
