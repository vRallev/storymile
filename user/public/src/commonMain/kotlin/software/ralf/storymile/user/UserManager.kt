package software.ralf.storymile.user

import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.scope.Scope

/** Owns the active user's child scope for the application lifetime. */
interface UserManager {
  /**
   * The active user's scope, initially for a guest user. Available after application startup and
   * before teardown starts. Obtain [User] from the scope's Metro graph.
   */
  val userScope: StateFlow<Scope>
}
