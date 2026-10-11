package software.ralf.storymile

import androidx.compose.ui.window.ComposeUIViewController
import dev.zacsweers.metro.createGraphFactory
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import software.ralf.storymile.scope.runtime.KmpApplication

/** Swift host that retains the application lifecycle across graph and scene recreation. */
class IosApplication(application: UIApplication) {
  private val application = KmpApplication {
    createGraphFactory<IosAppGraph.Factory>().create(application, it)
  }
    .apply { create() }

  /** Creates a view controller that follows the current app scope. */
  fun mainViewController(): UIViewController {
    return ComposeUIViewController { ApplicationContent(application) }
  }
}
