package software.ralf.storymile.user

import software.ralf.app.platform.scope.Scope

/** The owner of user data. A guest user requires no account. */
interface User {
  /** Stable local storage identity containing only letters, digits, underscores, and hyphens. */
  val id: String

  /** Whether this user uses the app without a signed-in account. */
  val isGuest: Boolean

  /**
   * This user's scope. Available after application startup and before teardown starts. Inject
   * user-owned services in this scope with [UserScope].
   */
  val scope: Scope
}
