package software.ralf.storymile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import software.ralf.app.platform.renderer.ComposeRendererFactory
import software.ralf.app.platform.renderer.renderCompose
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.scope.runtime.Application

/** Replaces the presenter tree and remembered UI state when the application restarts. */
@Composable
internal fun ApplicationContent(application: Application) {
  val rootScope by application.rootScopes.collectAsState()
  val scope = rootScope?.takeUnless { it.isDestroyed() } ?: return

  key(scope) {
    val templateProvider = remember {
      scope.metroDependencyGraph<AppGraph>().templateProviderFactory.createTemplateProvider()
    }
    DisposableEffect(templateProvider) { onDispose { templateProvider.cancel() } }

    val rendererFactory = remember {
      ComposeRendererFactory(
        object : RootScopeProvider {
          override val rootScope: Scope = scope
        },
      )
    }
    val template by templateProvider.templates.collectAsState()
    rendererFactory.renderCompose(template)
  }
}
