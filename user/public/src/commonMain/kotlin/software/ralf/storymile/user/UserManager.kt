package software.ralf.storymile.user

import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.scope.Scope

/** Owns the active user's child scope for the application lifetime. */
interface UserManager {
  /**
   * The active account or guest scope. Available after application startup and before teardown.
   * Obtain [User] from its Metro graph. A change of identity destroys the previous scope.
   */
  val userScope: StateFlow<Scope>
}
