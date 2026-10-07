package software.ralf.storymile.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@Inject
@ContributesBinding(AppScope::class)
class AndroidThemeEnvironment : ThemeEnvironment {
  @Composable
  override fun Provide(darkTheme: Boolean, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val themedConfiguration =
      remember(configuration, darkTheme) {
        Configuration(configuration).apply {
          val nightMode =
            if (darkTheme) {
              Configuration.UI_MODE_NIGHT_YES
            } else {
              Configuration.UI_MODE_NIGHT_NO
            }
          uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
      }
    CompositionLocalProvider(LocalConfiguration provides themedConfiguration, content = content)
  }
}
