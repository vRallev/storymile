package software.ralf.storymile.theme

/** Appearance choices available in Settings. */
enum class ThemeMode {
  /** Follow changes to the system appearance. */
  SYSTEM,

  /** Use the light palette. */
  LIGHT,

  /** Use the dark palette. */
  DARK;

  /** Returns whether the dark palette is active for the given system appearance. */
  fun isDark(systemDarkTheme: Boolean): Boolean =
    when (this) {
      SYSTEM -> systemDarkTheme
      LIGHT -> false
      DARK -> true
    }
}
