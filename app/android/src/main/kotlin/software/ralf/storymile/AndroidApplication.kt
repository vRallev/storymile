package software.ralf.storymile

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import software.ralf.storymile.app.android.BuildConfig
import software.ralf.storymile.util.BuildInfoImpl

/** Retains the shared application across activities without creating the app scope in onCreate. */
open class AndroidApplication : Application(), KmpApplication.Provider {
  override val kmpApplication by lazy { KmpApplication { metroGraph(it) } }

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
