package software.ralf.storymile

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.scope.Scope
import software.ralf.storymile.scope.runtime.Application as StorymileApplication
import software.ralf.storymile.scope.runtime.KmpApplication

/** Android application that creates the shared root scope. */
open class AndroidApplication : Application(), StorymileApplication {
  private val storymileApplication = KmpApplication { metroGraph(it) }

  override val rootScopes: StateFlow<Scope?>
    get() = storymileApplication.rootScopes

  override val rootScope: Scope
    get() = storymileApplication.rootScope

  override fun restart() {
    storymileApplication.restart()
  }

  override fun onCreate() {
    storymileApplication.create()
    super.onCreate()
  }

  /** Creates the production graph. Tests can override this to install a test graph. */
  protected open fun metroGraph(storymileApplication: StorymileApplication): AppGraph {
    return createGraphFactory<AndroidAppGraph.Factory>().create(this, storymileApplication)
  }
}
