package software.ralf.storymile

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import software.ralf.storymile.app.android.BuildConfig
import software.ralf.storymile.util.BuildInfoImpl

/** Retains startup across activities without constructing the app scope in onCreate. */
open class AndroidApplication : Application(), ApplicationStartup.Provider {
  override val startup by lazy { ApplicationStartup { metroGraph(it) } }

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
