package software.ralf.storymile.user

/** The owner of user data. A guest user requires no account. */
interface User {
  /** Stable local storage identity containing only letters, digits, underscores, and hyphens. */
  val id: String

  /** Whether this user uses the app without a signed-in account. */
  val isGuest: Boolean
}
