package software.ralf.storymile.user

class GoogleAccount(
  override val id: String,
  val email: String?,
  internal val accessToken: String,
) : User {
  override val isGuest: Boolean = false
}
