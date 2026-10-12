package software.ralf.storymile.restart

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.destroyAndWait

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultApplicationRestarter(
  private val rootScopeProvider: RootScopeProvider,
  private val processRestarter: ProcessRestarter,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ApplicationRestarter {
  private val restartRequested = MutableStateFlow(false)

  override fun restart() {
    if (!restartRequested.compareAndSet(expect = false, update = true)) {
      return
    }

    // This coroutine must outlive the app scope it destroys.
    CoroutineScope(mainDispatcher).launch {
      // Scope jobs are independent, so a timeout does not wait for their remaining cleanup.
      try {
        withTimeoutOrNull(5.seconds) { rootScopeProvider.rootScope.destroyAndWait() }
      } catch (_: Throwable) {
        // Restart even if scope cleanup fails.
      }
      processRestarter.restart()
    }
  }
}
