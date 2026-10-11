package software.ralf.storymile.user

import software.ralf.app.platform.scope.Scope

/** Provides the active user and owns its child scope for the application lifetime. */
interface UserManager {
  /** The active user. Starts as a guest user and is never null. */
  val user: User

  /**
   * The active user's scope. Available after application startup and until teardown. Inject
   * user-owned services in this scope with [UserScope].
   */
  val userScope: Scope
}
