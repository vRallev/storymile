package software.ralf.storymile

import android.app.Application
import com.jakewharton.processphoenix.ProcessPhoenix
import dev.zacsweers.metro.createGraphFactory
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.storymile.app.android.BuildConfig
import software.ralf.storymile.util.BuildInfoImpl

/** Android application that creates the shared root scope. */
open class AndroidApplication : Application(), RootScopeProvider {
  private val storymileApplication = KmpApplication()

  override val rootScope: Scope
    get() = storymileApplication.rootScope

  override fun onCreate() {
    super.onCreate()
    if (ProcessPhoenix.isPhoenixProcess(this)) {
      return
    }
    storymileApplication.create(metroGraph(storymileApplication))
  }

  /** Creates the production graph. Tests can override this to install a test graph. */
  protected open fun metroGraph(storymileApplication: KmpApplication): AppGraph {
    return createGraphFactory<AndroidAppGraph.Factory>()
      .create(
        application = this,
        rootScopeProvider = storymileApplication,
        buildInfo =
          BuildInfoImpl(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
          ),
      )
  }
}
