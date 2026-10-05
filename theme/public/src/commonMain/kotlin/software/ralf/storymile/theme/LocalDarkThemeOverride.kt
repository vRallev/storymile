package software.ralf.storymile.theme

import androidx.compose.runtime.compositionLocalOf

/** Overrides [StorymileTheme]'s system appearance choice. Null follows the system theme. */
val LocalDarkThemeOverride = compositionLocalOf<Boolean?> { null }
