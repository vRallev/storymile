package software.ralf.storymile.user

/** The feature tier for a user. */
enum class SubscriptionTier {
  /** The tier for guests and signed-in users without a subscription. */
  BASIC,

  /** The tier for signed-in users with a Plus subscription. */
  PLUS,
}
