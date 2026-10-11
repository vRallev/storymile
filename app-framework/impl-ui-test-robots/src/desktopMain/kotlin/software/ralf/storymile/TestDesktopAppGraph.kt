package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.RuntimeModeControllerImpl
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.RuntimeModeStorageBindings

/**
 * Desktop integration-test graph that includes production bindings and contributed test robots.
 * Each graph uses fake mode and fresh in-memory storage.
 *
 * Keeping this graph in the robot fixture module lets Metro discover every test robot without
 * adding robot dependencies to the production desktop application.
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
interface TestDesktopAppGraph {
  /** Mode controller locked to fake mode for this test application. */
  val runtimeModeController: RuntimeModeController

  /** Factory for a Desktop test graph. */
  @DependencyGraph.Factory
  interface Factory {
    /** Creates a graph using the test application's root-scope provider. */
    fun create(@Provides rootScopeProvider: RootScopeProvider): TestDesktopAppGraph
  }
}
