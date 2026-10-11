package software.ralf.storymile

import androidx.compose.runtime.Composable
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.storymile.scope.runtime.Application
import software.ralf.storymile.scope.runtime.KmpApplication

/** Owns the application lifecycle and renders each new app scope. */
class DesktopApp(graph: (Application) -> AppGraph) : RootScopeProvider {
  private val application = KmpApplication(graphFactory = graph).apply { create() }

  override val rootScope: Scope
    get() = application.rootScope

  /** Renders the current scope's templates. */
  @Composable
  fun renderTemplates() {
    ApplicationContent(application)
  }

  /** Releases presenter and application resources. */
  fun destroy() {
    application.destroy()
  }
}
