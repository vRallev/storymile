package software.ralf.storymile.theme

import androidx.compose.runtime.Composable

/** Makes theme-qualified Compose resources follow the app's appearance. */
interface ThemeEnvironment {
  /**
   * Applies [darkTheme] to resource selection within [content] without changing system settings.
   */
  @Composable fun Provide(darkTheme: Boolean, content: @Composable () -> Unit)
}
