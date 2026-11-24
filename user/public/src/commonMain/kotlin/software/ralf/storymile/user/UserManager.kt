package software.ralf.storymile.user

import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.scope.Scope

/** Owns the active user's child scope for the application lifetime. */
interface UserManager {
  /** The active identity. Available after [awaitReady] and before teardown starts. */
  val user: StateFlow<User>

  /**
   * The active user's scope. Available after [awaitReady] and before teardown starts. Its value is
   * null while the app replaces user storage.
   */
  val userScope: StateFlow<Scope?>

  /** Waits for the saved session to load and its user scope to start. */
  suspend fun awaitReady()

  /** Signs in with the fake account and keeps the active user's storage. */
  suspend fun signIn()

  /**
   * Signs out of the fake account. If [keepData] is false, starts a new guest scope with only the
   * old preferences and schedules the remaining old data for deletion.
   */
  suspend fun signOut(keepData: Boolean)
}
