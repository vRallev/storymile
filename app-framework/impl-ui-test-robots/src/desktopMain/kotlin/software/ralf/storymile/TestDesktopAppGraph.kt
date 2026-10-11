package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import software.ralf.storymile.scope.runtime.Application
import software.ralf.storymile.scope.runtime.DesktopProcessRestarter
import software.ralf.storymile.scope.runtime.ProcessRestarter
import software.ralf.storymile.storage.DefaultStorage

/**
 * Desktop integration-test graph that includes production bindings and contributed test robots.
 * Each graph uses fresh in-memory storage.
 *
 * Keeping this graph in the robot fixture module lets Metro discover every test robot without
 * adding robot dependencies to the production desktop application.
 */
@DependencyGraph(
  AppScope::class,
  excludes = [DefaultStorage.Factory::class, DesktopProcessRestarter::class],
)
interface TestDesktopAppGraph {
  /** Records restart requests without relaunching or terminating the test process. */
  val restartRecorder: RestartRecorder

  @Provides fun processRestarter(recorder: RestartRecorder): ProcessRestarter = recorder

  /** Factory for a Desktop test graph. */
  @DependencyGraph.Factory
  interface Factory {
    /** Creates a graph using the test application's root-scope provider. */
    fun create(@Provides application: Application): TestDesktopAppGraph
  }

  /** Observes cleanup at the point where the application requests a process restart. */
  @Inject
  @SingleIn(AppScope::class)
  class RestartRecorder : ProcessRestarter {
    val restarted = CompletableDeferred<Unit>()
    var onRestart: () -> Unit = {}

    override fun restart() {
      onRestart()
      restarted.complete(Unit)
    }
  }
}
