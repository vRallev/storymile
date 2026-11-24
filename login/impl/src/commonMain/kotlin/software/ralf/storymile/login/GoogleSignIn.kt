package software.ralf.storymile.login

interface GoogleSignIn {
  val available: Boolean

  /** Returns a Google ID token or null if the listener dismissed the account chooser. */
  suspend fun credential(): String?

  suspend fun signOut() {}
}
