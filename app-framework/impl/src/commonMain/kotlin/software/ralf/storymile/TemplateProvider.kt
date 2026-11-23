package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.presenter.compose.ComposePresenterScope
import software.ralf.app.platform.presenter.compose.ComposePresenterScopeFactory
import software.ralf.app.platform.presenter.compose.launchComposePresenter
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.approot.AppRootPresenter
import software.ralf.storymile.templates.AppTemplate
import software.ralf.storymile.templates.AppTemplatePresenter

/** Produces the application template stream for a platform renderer. */
@AssistedInject
class TemplateProvider(
  presenter: AppRootPresenter,
  templatePresenterFactory: AppTemplatePresenter.Factory,
  @Assisted private val composePresenterScope: ComposePresenterScope,
) {
  /** Templates emitted by the root presenter. */
  val templates: StateFlow<AppTemplate> by lazy {
    composePresenterScope
      .launchComposePresenter(
        presenter = templatePresenterFactory.createAppTemplatePresenter(presenter),
        input = Unit,
      )
      .model
  }

  /** Stops template production and releases presenter resources. */
  fun cancel() {
    composePresenterScope.cancel()
  }

  companion object {
    /** Creates a host's template stream with a fresh presenter scope. Call [cancel] on release. */
    fun create(scope: Scope): TemplateProvider {
      val graph = scope.metroDependencyGraph<Graph>()
      return graph.templateProviderFactory.create(
        graph.presenterScopeFactory.createComposePresenterScope(),
      )
    }
  }

  @AssistedFactory
  interface Factory {
    fun create(composePresenterScope: ComposePresenterScope): TemplateProvider
  }

  @ContributesTo(AppScope::class)
  interface Graph {
    val templateProviderFactory: Factory
    val presenterScopeFactory: ComposePresenterScopeFactory
  }
}
