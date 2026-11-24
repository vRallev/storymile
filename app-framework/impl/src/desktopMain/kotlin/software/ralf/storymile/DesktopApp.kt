package software.ralf.storymile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import software.ralf.app.platform.renderer.ComposeRendererFactory
import software.ralf.app.platform.renderer.renderCompose
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.templates.AppShortcuts
import software.ralf.storymile.theme.SkikoThemeEnvironment

/** Owns the Desktop root scope and renders its shared template stream. */
class DesktopApp(graph: (RootScopeProvider) -> AppGraph) : RootScopeProvider {
  private val kmpApplication = KmpApplication { graph(it) }
  private val templateProvider = lazy { TemplateProvider.create(rootScope) }

  override val rootScope: Scope
    get() = kmpApplication.rootScope

  /** Initializes the app without a rendered host, for headless callers and test setup. */
  suspend fun awaitReady() {
    kmpApplication.awaitReady()
  }

  /** Renders the latest template. */
  @Composable
  fun renderTemplates() {
    kmpApplication.Content(themeEnvironment = remember { SkikoThemeEnvironment() }) {
      val template by templateProvider.value.templates.collectAsState()
      val rendererFactory = remember { ComposeRendererFactory(this) }
      DesktopBackGesture { rendererFactory.renderCompose(template) }
    }
  }

  /** Runs the app action for a Command or Ctrl shortcut, using the current system appearance. */
  fun handleShortcut(key: Key, systemDarkTheme: Boolean): Boolean {
    if (!templateProvider.isInitialized()) {
      return false
    }
    val scope = rootScope
    if (scope.isDestroyed()) {
      return false
    }
    if (key == Key.D) {
      scope.metroDependencyGraph<AppGraph>().themeController.toggle(systemDarkTheme)
      return true
    }
    val action = scope.metroDependencyGraph<Graph>().shortcuts.actionForKey(key) ?: return false
    scope.launch { action() }
    return true
  }

  /** Releases presenter and application resources. */
  fun destroy() {
    if (templateProvider.isInitialized()) {
      templateProvider.value.cancel()
    }
    kmpApplication.destroy()
  }

  /** Graph access needed by the Desktop runtime. */
  @ContributesTo(AppScope::class)
  interface Graph {
    /** App actions available for Desktop shortcuts. */
    val shortcuts: AppShortcuts
  }
}
