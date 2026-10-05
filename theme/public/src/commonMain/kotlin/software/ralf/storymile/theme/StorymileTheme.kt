package software.ralf.storymile.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/**
 * Applies Paper Tide or Harbor Night around [content], using [LocalDarkThemeOverride] or the system
 * appearance by default.
 */
@Composable
fun StorymileTheme(
  darkTheme: Boolean = LocalDarkThemeOverride.current ?: isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme =
      if (darkTheme) StorymileColorSchemes.harborNight else StorymileColorSchemes.paperTide,
    typography = Typography(),
    content = content,
  )
}
