package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import software.ralf.storymile.scope.runtime.Application

/** Final Desktop application graph. */
@DependencyGraph(AppScope::class)
interface DesktopAppGraph {
  /** Factory for the Desktop application graph. */
  @DependencyGraph.Factory
  fun interface Factory {
    /** Creates a graph with the shared root-scope provider. */
    fun create(@Provides application: Application): DesktopAppGraph
  }
}
