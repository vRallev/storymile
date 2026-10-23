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
import software.ralf.storymile.theme.SkikoThemeEnvironment

/** Owns the Desktop root scope and renders its shared template stream. */
class DesktopApp(graph: (RootScopeProvider) -> AppGraph) : RootScopeProvider {
  private val startup = ApplicationStartup { graph(it) }
  private val templateProvider = lazy {
    rootScope.metroDependencyGraph<Graph>().templateProviderFactory.createTemplateProvider()
  }

  override val rootScope: Scope
    get() = startup.rootScope

  /** Initializes the app without a rendered host, for headless callers and test setup. */
  suspend fun awaitReady() {
    startup.awaitReady()
  }

  /** Renders the latest template. */
  @Composable
  fun renderTemplates() {
    startup.Content(themeEnvironment = remember { SkikoThemeEnvironment() }) {
      val template by templateProvider.value.templates.collectAsState()
      val rendererFactory = remember { ComposeRendererFactory(this) }
      DesktopBackGesture { rendererFactory.renderCompose(template) }
    }
  }

  /** Saves the opposite runtime mode and restarts the application. */
  fun toggleRuntimeMode() {
    if (!templateProvider.isInitialized()) {
      return
    }
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
    if (templateProvider.isInitialized()) {
      templateProvider.value.cancel()
    }
    startup.destroy()
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
