package software.ralf.storymile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.storymile.launchanimation.LaunchAnimation
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeEnvironment

/**
 * Owns startup before the app scope exists. Construction does no graph or storage work. Retain one
 * instance for the application lifetime and call [destroy] when that lifetime ends.
 */
class ApplicationStartup(
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
  graph: suspend (KmpApplication) -> AppGraph,
) : RootScopeProvider {
  private val application = KmpApplication()
  private val worker = CoroutineScope(SupervisorJob() + dispatcher)
  private val animation = LaunchAnimation()
  private val initialization =
    worker.async(start = CoroutineStart.LAZY) {
      try {
        application.create(graph(application))
        ensureActive()
      } catch (failure: Throwable) {
        application.destroy()
        throw failure
      }
    }

  /** Available after [awaitReady] completes or inside [Content]'s content. */
  override val rootScope: Scope
    get() {
      check(initialization.isCompleted && !initialization.isCancelled) { "Startup is not ready." }
      return application.rootScope
    }

  /** Starts initialization on the worker and waits without blocking the caller's thread. */
  suspend fun awaitReady() {
    initialization.await()
  }

  /** Shows the boat first, initializes the app, then renders and reveals [content]. */
  @Composable
  fun Content(
    themeEnvironment: ThemeEnvironment,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
  ) {
    var firstFrame by remember(this) { mutableStateOf(false) }
    var ready by remember(this) {
      mutableStateOf(initialization.isCompleted && !initialization.isCancelled)
    }
    LaunchedEffect(this, firstFrame) {
      if (firstFrame) {
        awaitReady()
        ready = true
      }
    }
    StorymileTheme(environment = themeEnvironment) {
      animation.Content(
        modifier = modifier,
        contentReady = ready,
        onFirstFrame = { firstFrame = true },
      ) {
        if (ready) {
          content()
        }
      }
    }
  }

  /** Cancels pending startup and releases the scope, including a scope still being created. */
  fun destroy() {
    worker.cancel()
    initialization.invokeOnCompletion { application.destroy() }
  }

  /** Supplies the process-owned startup to platform-created hosts, including Android activities. */
  interface Provider : RootScopeProvider {
    /** The same startup instance is retained across host recreation. */
    val startup: ApplicationStartup

    override val rootScope: Scope
      get() = startup.rootScope
  }
}
