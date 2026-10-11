package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Multibinds
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.util.Platform

/**
 * Shared interface for the application graph. Final graphs live in platform source sets so they can
 * provide platform-specific dependencies.
 */
@ContributesTo(AppScope::class)
interface AppGraph {
  /** Data source selected for this application. */
  val runtimeModeController: RuntimeModeController

  /** Platform used by the running application. */
  val platform: Platform

  /** Preferences, files, and caches owned by the app scope. */
  @ForScope(AppScope::class) val storage: Storage

  /** All [Scoped] instances that share the application lifecycle. */
  @ForScope(AppScope::class) @Multibinds(allowEmpty = true) val appScopedInstances: Set<Scoped>

  /** Coroutine scope that lives as long as the application scope. */
  @ForScope(AppScope::class) val appScopeCoroutineScopeScoped: CoroutineScopeScoped
}
