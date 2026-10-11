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
  private var userScopeOrNull: Scope? = null

  override val user: StateFlow<User>
    field = MutableStateFlow<User>(GuestUser)

  override val userScope: Scope
    get() = checkNotNull(userScopeOrNull) { "The application must be running to access UserScope." }

  override fun onEnterScope(scope: Scope) {
    check(userScopeOrNull == null) { "UserScope has already started." }
    val currentUser = user.value
    val graph = userGraphFactory.createUserGraph(currentUser)
    val child =
      scope.buildChild("user-${currentUser.id}") {
        addMetroDependencyGraph(graph)
        addCoroutineScopeScoped(graph.coroutineOwner)
      }

    userScopeOrNull = child
    child.register(graph.userScopedInstances)
  }

  override fun onExitScope() {
    // The parent destroys its children before invoking this callback.
    userScopeOrNull = null
  }
}
