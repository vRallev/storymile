package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import software.ralf.storymile.application.Application

/** Final Wasm application graph. */
@DependencyGraph(AppScope::class)
interface WasmJsAppGraph {
  /** Factory for the Wasm application graph. */
  @DependencyGraph.Factory
  fun interface Factory {
    /** Creates a graph with the shared root-scope provider. */
    fun create(@Provides application: Application): WasmJsAppGraph
  }
}
