package software.ralf.storymile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.templates.AppTemplate
import software.ralf.storymile.util.rootScopeProvider

/** Retains the shared template stream across Android configuration changes. */
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
  private val graph = application.rootScopeProvider().rootScope.metroDependencyGraph<AppGraph>()
  private val templateProvider = graph.templateProviderFactory.createTemplateProvider()

  /** Application templates retained independently of Activity recreation. */
  val templates: StateFlow<AppTemplate> = templateProvider.templates

  override fun onCleared() {
    templateProvider.cancel()
  }
}
