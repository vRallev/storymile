package software.ralf.storymile

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.storymile.runtimemode.RuntimeModeControllerImpl
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.RuntimeModeStorageBindings

/**
 * Android integration-test graph that includes production bindings and contributed test robots.
 * Each graph uses fake mode and fresh in-memory storage.
 *
 * The graph is compiled in the robot fixture module so Metro can discover robots that are absent
 * from the production application classpath.
 */
@DependencyGraph(
  AppScope::class,
  excludes =
    [
      DefaultStorage.Factory::class,
      RuntimeModeControllerImpl::class,
      RuntimeModeStorageBindings::class,
    ],
)
interface TestAndroidAppGraph {
  /** Factory for an Android test graph. */
  @DependencyGraph.Factory
  interface Factory {
    /** Creates a graph using the test application and its root-scope provider. */
    fun create(
      @Provides application: Application,
      @Provides rootScopeProvider: RootScopeProvider,
    ): TestAndroidAppGraph
  }
}
