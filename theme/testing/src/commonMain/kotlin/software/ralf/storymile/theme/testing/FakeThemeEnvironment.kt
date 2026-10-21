package software.ralf.storymile.theme.testing

import androidx.compose.runtime.Composable
import software.ralf.storymile.theme.ThemeEnvironment

/** Renders content without changing resource selection or system settings. */
class FakeThemeEnvironment : ThemeEnvironment {
  @Composable
  override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
    content()
  }
}
