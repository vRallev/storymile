package software.ralf.storymile.scope.runtime

import software.ralf.app.platform.scope.RootScopeProvider

/** Controls the application lifetime. */
interface Application : RootScopeProvider {
  /**
   * Destroys the app scope, waits up to five seconds for coroutine cleanup, then restarts the app.
   *
   * Android and Desktop launch a new process. Web reloads the page. iOS closes the app without a
   * dialog; the user must open it again. Saved data is preserved. Synchronous scope callbacks must
   * return promptly so they do not delay the restart.
   *
   * This returns immediately and can be called from any thread, including an outgoing presenter.
   * Repeated requests are combined. Requests before startup or after shutdown are ignored.
   */
  fun restart()
}
