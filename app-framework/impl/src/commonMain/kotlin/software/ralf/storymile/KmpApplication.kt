package software.ralf.storymile

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.flow.MutableStateFlow
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.di.metro.addMetroDependencyGraph
import software.ralf.app.platform.scope.register
import software.ralf.storymile.launchanimation.LaunchAnimation
import software.ralf.storymile.theme.LocalDarkThemeOverride
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeController
import software.ralf.storymile.theme.ThemeEnvironment

/**
 * Owns the application lifecycle and cold-launch reveal. Construction does no graph or storage
 * work. Retain one instance for the application lifetime and call [destroy] when that lifetime
 * ends.
 */
class KmpApplication(
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
  graph: suspend (KmpApplication) -> AppGraph,
) : RootScopeProvider {
  private var rootScopeOrNull: Scope? = null
  private val worker = CoroutineScope(SupervisorJob() + dispatcher)
  private val launchProgress = Animatable(0f)
  private val launchThemeProgress = Animatable(0f)
  private var firstLaunchDarkTheme: Boolean? = null
  private val launchThemeController = MutableStateFlow<ThemeController?>(null)
  private val initialization =
    worker.async(start = CoroutineStart.LAZY) {
      try {
        val appGraph = graph(this@KmpApplication)
        rootScopeOrNull = Scope.buildRootScope {
          addMetroDependencyGraph(appGraph)
          addCoroutineScopeScoped(appGraph.appScopeCoroutineScopeScoped)
        }
        val themeLoading = async {
          val controller = appGraph.themeController
          controller.awaitInitialMode()
          launchThemeController.value = controller
        }
        // Publish before registration because lifecycle callbacks can access the root scope.
        rootScope.register(appGraph.appScopedInstances)
        themeLoading.await()
        ensureActive()
      } catch (failure: Throwable) {
        destroyScope()
        throw failure
      }
    }

  /**
   * Available to lifecycle callbacks during registration, then to callers after [awaitReady] or
   * inside [Content]'s content.
   */
  override val rootScope: Scope
    get() = checkNotNull(rootScopeOrNull) { "The application scope is not available." }

  /** Starts initialization on the worker and waits without blocking the caller's thread. */
  suspend fun awaitReady() {
    initialization.await()
  }

  /**
   * Shows the boat in the system appearance and animates to the saved choice before revealing
   * [content]. Calls [onAppearanceChanged] with the visible appearance for platform chrome.
   */
  @Composable
  fun Content(
    themeEnvironment: ThemeEnvironment,
    modifier: Modifier = Modifier,
    onAppearanceChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
  ) {
    var firstFrame by remember(this) { mutableStateOf(false) }
    var ready by
      remember(this) {
        mutableStateOf(initialization.isCompleted && !initialization.isCancelled)
      }
    LaunchedEffect(this, firstFrame) {
      if (firstFrame) {
        awaitReady()
        ready = true
      }
    }
    val controller by launchThemeController.collectAsState()
    val mode = controller?.mode?.collectAsState()?.value
    val hostDarkTheme = LocalDarkThemeOverride.current ?: isSystemInDarkTheme()
    val initialDarkTheme = firstLaunchDarkTheme ?: hostDarkTheme
    val darkTheme = mode?.isDark(hostDarkTheme) ?: initialDarkTheme
    val contentReady = ready && mode != null
    val useTargetChrome by
      remember(this) {
        derivedStateOf { launchProgress.value >= 1f || launchThemeProgress.value >= 0.5f }
      }
    StorymileTheme(environment = themeEnvironment, darkTheme = darkTheme) {
      SideEffect {
        if (firstLaunchDarkTheme == null) {
          firstLaunchDarkTheme = initialDarkTheme
        }
        onAppearanceChanged(if (useTargetChrome) darkTheme else initialDarkTheme)
      }
      LaunchAnimation(
        themeEnvironment = themeEnvironment,
        initialDarkTheme = initialDarkTheme,
        darkTheme = darkTheme,
        themeProgress = launchThemeProgress,
        progress = launchProgress,
        modifier = modifier,
        contentReady = contentReady,
        onFirstFrame = { firstFrame = true },
      ) {
        if (contentReady) {
          content()
        }
      }
    }
  }

  /** Cancels pending startup and releases the scope, including a scope still being created. */
  fun destroy() {
    worker.cancel()
    initialization.invokeOnCompletion { destroyScope() }
  }

  private fun destroyScope() {
    rootScopeOrNull?.destroy()
    rootScopeOrNull = null
  }

  /**
   * Supplies the process-owned application to platform-created hosts, including Android activities.
   */
  interface Provider : RootScopeProvider {
    /** The same application instance is retained across host recreation. */
    val kmpApplication: KmpApplication

    override val rootScope: Scope
      get() = kmpApplication.rootScope
  }
}
