package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import platform.UIKit.UIApplication
import software.ralf.storymile.application.Application

/** Final iOS graph, including dependencies supplied by UIKit. */
@DependencyGraph(AppScope::class)
interface IosAppGraph {
  /** Factory for the iOS application graph. */
  @DependencyGraph.Factory
  fun interface Factory {
    /** Creates a graph with UIKit and application lifecycle dependencies. */
    fun create(
      @Provides uiApplication: UIApplication,
      @Provides application: Application,
    ): IosAppGraph
  }
}
