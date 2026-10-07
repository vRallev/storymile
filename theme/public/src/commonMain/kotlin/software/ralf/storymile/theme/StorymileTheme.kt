package software.ralf.storymile.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

private val storymileTypography =
  Typography().run {
    copy(titleLarge = titleLarge.copy(fontSize = 30.sp, lineHeight = 38.sp))
  }

@Suppress("unused")
private val storymileTypographyLarge =
  Typography().run {
    copy(
      displayLarge = displayLarge.copy(fontSize = 72.sp, lineHeight = 80.sp),
      displayMedium = displayMedium.copy(fontSize = 56.sp, lineHeight = 64.sp),
      displaySmall = displaySmall.copy(fontSize = 44.sp, lineHeight = 52.sp),
      headlineLarge = headlineLarge.copy(fontSize = 40.sp, lineHeight = 48.sp),
      headlineMedium = headlineMedium.copy(fontSize = 34.sp, lineHeight = 42.sp),
      headlineSmall = headlineSmall.copy(fontSize = 30.sp, lineHeight = 38.sp),
      titleLarge = titleLarge.copy(fontSize = 30.sp, lineHeight = 38.sp),
      titleMedium = titleMedium.copy(fontSize = 22.sp, lineHeight = 30.sp),
      titleSmall = titleSmall.copy(fontSize = 20.sp, lineHeight = 28.sp),
      bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
      bodyMedium = bodyMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
      bodySmall = bodySmall.copy(fontSize = 16.sp, lineHeight = 24.sp),
      labelLarge = labelLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
      labelMedium = labelMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
      labelSmall = labelSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
    )
  }

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
    typography = storymileTypography,
    content = content,
  )
}
