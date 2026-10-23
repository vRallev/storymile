package software.ralf.storymile

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import software.ralf.app.platform.renderer.ComposeRendererFactory
import software.ralf.app.platform.renderer.renderCompose
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.theme.SkikoThemeEnvironment

/** Called from Swift to create the Compose Multiplatform view controller. */
@Suppress("unused")
fun mainViewController(startup: ApplicationStartup): UIViewController = ComposeUIViewController {
  startup.Content(themeEnvironment = remember { SkikoThemeEnvironment() }) {
    val templateProvider = remember {
      startup.rootScope
        .metroDependencyGraph<IosMainViewControllerGraph>()
        .templateProviderFactory
        .createTemplateProvider()
    }

    DisposableEffect(templateProvider) { onDispose { templateProvider.cancel() } }

    val rendererFactory = remember { ComposeRendererFactory(startup) }
    val template by templateProvider.templates.collectAsState()

    rendererFactory.renderCompose(template)
  }
}
