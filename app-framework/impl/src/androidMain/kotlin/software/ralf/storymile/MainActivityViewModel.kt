package software.ralf.storymile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.templates.AppTemplate
import software.ralf.storymile.util.rootScopeProvider

/** Retains the shared template stream across Android configuration changes. */
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
  private val templateProvider = TemplateProvider.create(application.rootScopeProvider().rootScope)

  /** Application templates retained independently of Activity recreation. */
  val templates: StateFlow<AppTemplate> = templateProvider.templates

  override fun onCleared() {
    templateProvider.cancel()
  }
}
