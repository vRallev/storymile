package software.ralf.storymile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import software.ralf.app.platform.renderer.ComposeRendererFactory
import software.ralf.app.platform.renderer.renderCompose
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.scope.runtime.Application

/** Renders the presenter tree owned by the application scope. */
@Composable
internal fun ApplicationContent(application: Application) {
  val templateProvider = remember {
    application.rootScope
      .metroDependencyGraph<AppGraph>()
      .templateProviderFactory
      .createTemplateProvider()
  }
  DisposableEffect(templateProvider) { onDispose { templateProvider.cancel() } }

  val rendererFactory = remember { ComposeRendererFactory(application) }
  val template by templateProvider.templates.collectAsState()
  rendererFactory.renderCompose(template)
}
