package software.ralf.storymile

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import software.ralf.app.platform.renderer.ComposeAndroidRendererFactory
import software.ralf.app.platform.renderer.renderCompose
import software.ralf.storymile.theme.AndroidThemeEnvironment
import software.ralf.storymile.util.rootScopeProvider

/** Android entry point that renders the shared template stream. */
class MainActivity : ComponentActivity() {
  private val viewModel by viewModels<MainActivityViewModel>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // The shared boat reveal owns the launch transition.
      splashScreen.setOnExitAnimationListener { it.remove() }
    }
    enableEdgeToEdge()

    val startup = (application as ApplicationStartup.Provider).startup
    setContent {
      // The Android interop renderer does not forward the root modifier.
      Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        startup.Content(themeEnvironment = remember { AndroidThemeEnvironment() }) {
          val rendererFactory = remember {
            ComposeAndroidRendererFactory.createForComposeUi(rootScopeProvider = rootScopeProvider())
          }
          val template by viewModel.templates.collectAsState()
          rendererFactory.renderCompose(template)
        }
      }
    }
  }
}
