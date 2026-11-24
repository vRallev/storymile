package software.ralf.storymile.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme

class SkikoThemeEnvironment : ThemeEnvironment {
  // Compose resources still use this API; no replacement is available in Compose 1.12.1.
  @Suppress("DEPRECATION")
  @OptIn(InternalComposeUiApi::class)
  @Composable
  override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
      LocalSystemTheme provides if (darkTheme) SystemTheme.Dark else SystemTheme.Light,
      content = content,
    )
  }
}
