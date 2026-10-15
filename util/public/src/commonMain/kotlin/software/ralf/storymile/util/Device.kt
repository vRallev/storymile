package software.ralf.storymile.util

/** Device settings available to shared features. */
interface Device {
  /**
   * Uppercase two-letter country code from the current locale, or null when unavailable.
   *
   * Android and web infer a missing country from the language and script when possible.
   */
  val countryCode: String?
}
