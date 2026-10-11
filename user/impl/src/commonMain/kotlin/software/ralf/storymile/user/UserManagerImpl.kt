package software.ralf.storymile.user

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.di.metro.addMetroDependencyGraph
import software.ralf.app.platform.scope.register

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class UserManagerImpl(private val userGraphFactory: UserGraph.Factory) : UserManager, Scoped {
  private var scopeFlowOrNull: MutableStateFlow<Scope>? = null

  override val userScope: StateFlow<Scope>
    get() = checkNotNull(scopeFlowOrNull) { "The user scope is not active." }

  override fun onEnterScope(scope: Scope) {
    val guestUser = GuestUser()
    val graph = userGraphFactory.createUserGraph(guestUser)
    val child =
      scope.buildChild("user-${guestUser.id}") {
        addMetroDependencyGraph(graph)
        addCoroutineScopeScoped(graph.coroutineOwner)
      }

    scopeFlowOrNull = MutableStateFlow(child)
    child.register(graph.userScopedInstances)
  }

  override fun onExitScope() {
    scopeFlowOrNull = null
  }
}
