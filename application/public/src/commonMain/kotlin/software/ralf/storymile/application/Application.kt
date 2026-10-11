package software.ralf.storymile.application

import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope

/** Controls the application lifetime across app scopes. */
interface Application : RootScopeProvider {
  /**
   * The active app scope, or `null` before startup, during restart, and after shutdown.
   *
   * Hosts must replace their presenters, renderer factories, and rendered state when this changes.
   * A new scope is published after its lifecycle services have started.
   */
  val rootScopes: StateFlow<Scope?>

  /**
   * Requests a fresh app scope and graph after the current scope and its coroutine work stop.
   *
   * This returns immediately and can be called from any thread, including an outgoing presenter.
   * Requests during a restart can be combined. Requests before startup or after shutdown are
   * ignored. Saved data is preserved.
   */
  fun restart()
}
