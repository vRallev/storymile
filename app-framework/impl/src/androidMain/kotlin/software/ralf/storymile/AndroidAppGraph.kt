package software.ralf.storymile

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.storymile.util.BuildInfo
import software.ralf.storymile.util.BuildInfoImpl

/** Final Android graph, including dependencies supplied by the Android application. */
@DependencyGraph(AppScope::class, excludes = [BuildInfoImpl::class])
interface AndroidAppGraph {
  /** Factory for the Android application graph. */
  @DependencyGraph.Factory
  fun interface Factory {
    /** Creates a graph with Android and root-scope dependencies. */
    fun create(
      @Provides application: Application,
      @Provides rootScopeProvider: RootScopeProvider,
      @Provides buildInfo: BuildInfo,
    ): AndroidAppGraph
  }
}
