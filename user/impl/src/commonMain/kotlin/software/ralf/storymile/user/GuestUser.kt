package software.ralf.storymile.user

internal class GuestUser : User {
  override val id: String = "guest"
  override val isGuest: Boolean = true
}
