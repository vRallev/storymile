package software.ralf.storymile.scope.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.destroyAndWait
import software.ralf.app.platform.scope.di.metro.addMetroDependencyGraph
import software.ralf.app.platform.scope.register

/** Owns app scopes and coordinates restarts outside the scope being destroyed. */
class KmpApplication(
  dispatcher: CoroutineDispatcher = Dispatchers.Main,
  private val graphFactory: (Application) -> ApplicationGraph,
) : Application {
  private val lifecycleScope =
    CoroutineScope(dispatcher + SupervisorJob() + CoroutineName("ApplicationLifecycle"))
  private var rootScopeOrNull: Scope? = null
  private var created = false
  private var destroyed = false
  private val restartPending = MutableStateFlow(false)

  override val rootScopes: StateFlow<Scope?>
    field = MutableStateFlow(null)

  override val rootScope: Scope
    get() =
      checkNotNull(rootScopeOrNull?.takeUnless { it.isDestroyed() }) {
        "The application has no active app scope."
      }

  /** Starts the application once. Call from the host's main thread before rendering. */
  fun create() {
    check(!created && !destroyed) { "create() should be called only once." }
    created = true
    createRootScope()
  }

  override fun restart() {
    if (rootScopes.value == null || !restartPending.compareAndSet(expect = false, update = true)) {
      return
    }

    lifecycleScope.launch {
      try {
        val outgoingScope = rootScopeOrNull
        if (destroyed || outgoingScope == null) {
          return@launch
        }

        rootScopes.value = null
        outgoingScope.destroyAndWait()
        rootScopeOrNull = null
        if (!destroyed) {
          createRootScope()
        }
      } finally {
        restartPending.value = false
      }
    }
  }

  /** Stops the application permanently. Call from the host's main thread. */
  fun destroy() {
    destroyed = true
    rootScopes.value = null
    try {
      rootScopeOrNull?.destroy()
    } finally {
      rootScopeOrNull = null
      lifecycleScope.cancel()
    }
  }

  private fun createRootScope() {
    val graph = graphFactory(this)
    val scope = Scope.buildRootScope {
      addMetroDependencyGraph(graph)
      addCoroutineScopeScoped(graph.appScopeCoroutineScopeScoped)
    }
    rootScopeOrNull = scope
    var started = false
    try {
      scope.register(graph.appScopedInstances)
      rootScopes.value = scope
      started = true
    } finally {
      if (!started) {
        try {
          scope.destroy()
        } finally {
          rootScopeOrNull = null
        }
      }
    }
  }
}
