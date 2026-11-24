package software.ralf.storymile.user

interface GoogleLoginPlatform {
  val available: Boolean

  suspend fun signIn(): GoogleAccount?

  suspend fun resumeSignIn(): GoogleAccount? = null

  suspend fun signOut()
}
