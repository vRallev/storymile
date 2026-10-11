package software.ralf.storymile

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.storymile.runtimemode.FakeRuntimeModeController
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.RuntimeModeControllerImpl
import software.ralf.storymile.storage.RuntimeModeStorageBindings

/** Production graph locked to fake mode for the blackbox test build. */
@DependencyGraph(
  AppScope::class,
  excludes = [RuntimeModeControllerImpl::class, RuntimeModeStorageBindings::class],
)
interface FakeAndroidAppGraph {
  @Provides fun runtimeModeController(): RuntimeModeController = FakeRuntimeModeController()

  /** Factory for the blackbox application graph. */
  @DependencyGraph.Factory
  interface Factory {
    /** Creates a graph that uses in-memory app and user storage. */
    fun create(
      @Provides application: Application,
      @Provides rootScopeProvider: RootScopeProvider,
    ): FakeAndroidAppGraph
  }
}
