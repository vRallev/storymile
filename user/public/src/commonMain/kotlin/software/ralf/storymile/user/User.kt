package software.ralf.storymile.user

/**
 * The owner of user data. Injected values reflect updates within their user scope. Observe
 * [UserManager.user] to react to identity changes.
 */
interface User {
  /** Stable local storage identity. It stays the same when the user keeps their data. */
  val storageId: String

  /** Whether this user uses the app without a signed-in account. */
  val isGuest: Boolean

  /** The active subscription tier. Guests always have [SubscriptionTier.BASIC]. */
  val subscriptionTier: SubscriptionTier
}
