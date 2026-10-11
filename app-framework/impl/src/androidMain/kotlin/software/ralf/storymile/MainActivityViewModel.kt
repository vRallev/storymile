package software.ralf.storymile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.scope.runtime.Application as StorymileApplication
import software.ralf.storymile.templates.AppTemplate

/** Retains presenters across configuration changes and replaces them after an app restart. */
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
  private val applicationLifecycle = application as StorymileApplication

  /** Current scope and its templates, or null while the application restarts. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val content: StateFlow<Content?> =
    applicationLifecycle.rootScopes
      .flatMapLatest { scope ->
        if (scope == null || scope.isDestroyed()) {
          flowOf(null)
        } else {
          flow<Content?> {
            val provider =
              scope
                .metroDependencyGraph<AppGraph>()
                .templateProviderFactory
                .createTemplateProvider()
            try {
              emit(Content(scope, provider.templates))
              awaitCancellation()
            } finally {
              provider.cancel()
            }
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  /** Keeps a template stream paired with the graph that produces it. */
  class Content(val scope: Scope, val templates: StateFlow<AppTemplate>)
}
