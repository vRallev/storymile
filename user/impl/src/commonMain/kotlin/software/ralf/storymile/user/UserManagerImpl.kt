package software.ralf.storymile.user

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.di.metro.addMetroDependencyGraph
import software.ralf.app.platform.scope.register

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class UserManagerImpl(
  private val userGraphFactory: UserGraph.Factory,
  private val login: GoogleLogin,
  @MainCoroutineDispatcher private val mainDispatcher: CoroutineDispatcher,
) : UserManager, Scoped {
  private var scopeFlowOrNull: MutableStateFlow<Scope>? = null

  override val userScope: StateFlow<Scope>
    get() = checkNotNull(scopeFlowOrNull) { "The user scope is not active." }

  override fun onEnterScope(scope: Scope) {
    val guest = GuestUser()
    var active = (login.state.value as? GoogleLogin.State.SignedIn)?.user ?: guest
    activate(scope, active)
    scope.launch(mainDispatcher) {
      login.state.collect { state ->
        val user = (state as? GoogleLogin.State.SignedIn)?.user ?: guest
        if (user.id != active.id) {
          activate(scope, user)
          active = user
        }
      }
    }
  }

  override fun onExitScope() {
    scopeFlowOrNull = null
  }

  private fun activate(parent: Scope, user: User) {
    val graph = userGraphFactory.createUserGraph(user)
    val child =
      parent.buildChild("user-${user.id}") {
        addMetroDependencyGraph(graph)
        addCoroutineScopeScoped(graph.coroutineOwner)
      }
    val current = scopeFlowOrNull
    if (current == null) {
      scopeFlowOrNull = MutableStateFlow(child)
    } else {
      current.value.destroy()
      current.value = child
    }
    child.register(graph.userScopedInstances)
  }
}
