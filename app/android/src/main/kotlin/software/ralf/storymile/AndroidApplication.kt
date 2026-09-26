package software.ralf.storymile

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope

/** Android application that creates the shared root scope. */
open class AndroidApplication : Application(), RootScopeProvider {
  private val storymileApplication = KmpApplication()

  override val rootScope: Scope
    get() = storymileApplication.rootScope

  override fun onCreate() {
    storymileApplication.create(metroGraph(storymileApplication))
    super.onCreate()
  }

  /** Creates the production graph. Tests can override this to install a test graph. */
  protected open fun metroGraph(storymileApplication: KmpApplication): AppGraph {
    return createGraphFactory<AndroidAppGraph.Factory>().create(this, storymileApplication)
  }
}
