package software.ralf.storymile.user

internal data object GuestUser : User {
  override val id: String = "guest"
  override val isGuest: Boolean = true
}
