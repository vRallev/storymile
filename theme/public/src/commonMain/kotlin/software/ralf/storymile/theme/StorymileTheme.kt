package software.ralf.storymile.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Installs the application's standard Material 3 light or dark theme around [content]. */
@Composable
fun StorymileTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    typography = Typography(),
    content = content,
  )
}
