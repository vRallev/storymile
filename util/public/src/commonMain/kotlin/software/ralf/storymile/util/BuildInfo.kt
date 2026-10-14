package software.ralf.storymile.util

/** Metadata for the application build. */
interface BuildInfo {
  /** User-visible application version. */
  val versionName: String

  /** Numeric application version, shared with Android's version code. */
  val versionCode: Int

  /** Full Git commit SHA of the source checkout used for this build. */
  val gitSha: String
}
