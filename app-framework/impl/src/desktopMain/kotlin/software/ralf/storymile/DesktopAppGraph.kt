package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import software.ralf.app.platform.scope.RootScopeProvider

/** Final Desktop application graph. */
@DependencyGraph(AppScope::class)
interface DesktopAppGraph {
  /** Factory for the Desktop application graph. */
  @DependencyGraph.Factory
  fun interface Factory {
    /** Creates a graph with the root-scope provider and Desktop launch arguments. */
    fun create(
      @Provides rootScopeProvider: RootScopeProvider,
      @Provides @Named("desktopMainClass") mainClassName: String,
      @Provides @Named("desktopArguments") arguments: Array<String>,
    ): DesktopAppGraph
  }
}
