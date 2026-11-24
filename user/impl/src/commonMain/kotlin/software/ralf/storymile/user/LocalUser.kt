package software.ralf.storymile.user

internal class LocalUser(accountId: String?) : User {
  override val id: String = accountId ?: "guest"
  override val isGuest: Boolean = accountId == null
  override val subscriptionTier: SubscriptionTier = SubscriptionTier.BASIC
}
