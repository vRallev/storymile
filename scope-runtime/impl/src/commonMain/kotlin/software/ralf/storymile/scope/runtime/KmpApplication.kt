package software.ralf.storymile.scope.runtime

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.destroyAndWait
import software.ralf.app.platform.scope.di.metro.addMetroDependencyGraph
import software.ralf.app.platform.scope.register

/** Owns the app scope and coordinates restart outside the scope being destroyed. */
class KmpApplication(
  dispatcher: CoroutineDispatcher = Dispatchers.Main,
  private val graphFactory: (Application) -> ApplicationGraph,
) : Application {
  private val lifecycleScope =
    CoroutineScope(dispatcher + SupervisorJob() + CoroutineName("ApplicationLifecycle"))
  private val currentScope = MutableStateFlow<Scope?>(null)
  private var created = false
  private var destroyed = false
  private val restartPending = MutableStateFlow(false)
  private lateinit var processRestarter: ProcessRestarter

  override val rootScope: Scope
    get() =
      checkNotNull(currentScope.value?.takeUnless { it.isDestroyed() }) {
        "The application has no active app scope."
      }

  /** Starts the application once. Call from the host's main thread before rendering. */
  fun create() {
    check(!created && !destroyed) { "create() should be called only once." }
    created = true
    val graph = graphFactory(this)
    processRestarter = graph.processRestarter
    val scope = Scope.buildRootScope {
      addMetroDependencyGraph(graph)
      addCoroutineScopeScoped(graph.appScopeCoroutineScopeScoped)
    }
    currentScope.value = scope
    var started = false
    try {
      scope.register(graph.appScopedInstances)
      started = true
    } finally {
      if (!started) {
        try {
          scope.destroy()
        } finally {
          currentScope.value = null
        }
      }
    }
  }

  override fun restart() {
    if (
      currentScope.value == null || !restartPending.compareAndSet(expect = false, update = true)
    ) {
      return
    }

    lifecycleScope.launch {
      val outgoingScope = currentScope.value ?: return@launch
      currentScope.value = null
      // Scope jobs belong to the outgoing scope, so cancellation of this wait does not await them.
      // A cleanup failure must not prevent the platform reset.
      runCatching { withTimeoutOrNull(5.seconds) { outgoingScope.destroyAndWait() } }
      ensureActive()
      processRestarter.restart()
    }
  }

  /** Stops the application permanently. Call from the host's main thread. */
  fun destroy() {
    destroyed = true
    val scope = currentScope.value
    currentScope.value = null
    try {
      scope?.destroy()
    } finally {
      lifecycleScope.cancel()
    }
  }
}
