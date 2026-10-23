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
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.theme.SkikoThemeEnvironment

/** Owns the Wasm root scope and renders its shared template stream. */
class WasmJsApp(graph: (RootScopeProvider) -> AppGraph) : RootScopeProvider {
  private val startup = ApplicationStartup { graph(it) }
  private val templateProvider = lazy {
    rootScope.metroDependencyGraph<Graph>().templateProviderFactory.createTemplateProvider()
  }

  override val rootScope: Scope
    get() = startup.rootScope

  /** Renders the latest template. */
  @Composable
  fun renderTemplates() {
    startup.Content(themeEnvironment = remember { SkikoThemeEnvironment() }) {
      val template by templateProvider.value.templates.collectAsState()
      val rendererFactory = remember { ComposeRendererFactory(this) }
      rendererFactory.renderCompose(template)
    }
  }

  /** Releases presenter and application resources. */
  fun destroy() {
    if (templateProvider.isInitialized()) {
      templateProvider.value.cancel()
    }
    startup.destroy()
  }

  /** Graph access needed by the Wasm runtime. */
  @ContributesTo(AppScope::class)
  interface Graph {
    /** Factory used to create the browser's independently cancellable template stream. */
    val templateProviderFactory: TemplateProvider.Factory
  }
}
