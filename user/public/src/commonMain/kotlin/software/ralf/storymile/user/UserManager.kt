package software.ralf.storymile.user

import kotlinx.coroutines.flow.StateFlow

/** Provides the active user and owns its child scope for the application lifetime. */
interface UserManager {
  /** The active user. Starts as a guest user and is never null. */
  val user: StateFlow<User>
}
