package software.ralf.storymile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import software.ralf.app.platform.renderer.ComposeRendererFactory
import software.ralf.app.platform.renderer.renderCompose
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

/** Owns the Desktop root scope and renders its shared template stream. */
class DesktopApp(private val graph: (RootScopeProvider) -> AppGraph) : RootScopeProvider {
  private val application = KmpApplication().apply { create(graph(this)) }
  private val templateProvider =
    application.rootScope
      .metroDependencyGraph<Graph>()
      .templateProviderFactory
      .createTemplateProvider()

  override val rootScope: Scope
    get() = application.rootScope

  /** Renders the latest template. */
  @Composable
  fun renderTemplates() {
    val template by templateProvider.templates.collectAsState()
    val rendererFactory = remember { ComposeRendererFactory(application) }

    DesktopBackGesture { rendererFactory.renderCompose(template) }
  }

  /** Saves the opposite runtime mode and restarts the application. */
  fun toggleRuntimeMode() {
    val scope = rootScope
    if (scope.isDestroyed()) {
      return
    }
    val controller = scope.metroDependencyGraph<Graph>().runtimeModeController
    scope.launch {
      val mode =
        when (controller.mode) {
          RuntimeMode.Real -> RuntimeMode.Fake
          RuntimeMode.Fake -> RuntimeMode.Real
        }
      controller.switchMode(mode)
    }
  }

  /** Releases presenter and application resources. */
  fun destroy() {
    templateProvider.cancel()
    application.destroy()
  }

  /** Graph access needed by the Desktop runtime. */
  @ContributesTo(AppScope::class)
  interface Graph {
    /** Controller used to persist runtime mode changes and restart the application. */
    val runtimeModeController: RuntimeModeController

    /** Factory used to create the window's independently cancellable template stream. */
    val templateProviderFactory: TemplateProvider.Factory
  }
}
