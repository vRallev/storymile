package software.ralf.storymile.scope.runtime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Multibinds
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped

/** Lifecycle dependencies supplied by the platform graph. */
@ContributesTo(AppScope::class)
interface ApplicationGraph {
  /** Services registered after the root scope becomes available. */
  @ForScope(AppScope::class) @Multibinds(allowEmpty = true) val appScopedInstances: Set<Scoped>

  /** Owns coroutine work canceled when this graph's app scope ends. */
  @ForScope(AppScope::class) val appScopeCoroutineScopeScoped: CoroutineScopeScoped

  /** Restarts or terminates the current process after scope cleanup. */
  val processRestarter: ProcessRestarter
}
