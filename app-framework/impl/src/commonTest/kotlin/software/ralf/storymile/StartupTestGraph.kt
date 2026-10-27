package software.ralf.storymile

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.SupervisorJob
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.util.Platform

internal class StartupTestGraph(
  dispatcher: CoroutineDispatcher,
  override val appScopedInstances: Set<Scoped> = emptySet(),
) : AppGraph {
  override val platform: Platform = Platform.Desktop.Linux
  override val storage: Storage
    get() = error("Startup lifecycle tests do not use storage.")

  override val appScopeCoroutineScopeScoped =
    CoroutineScopeScoped(dispatcher + SupervisorJob() + CoroutineName("StartupTest"))
}
