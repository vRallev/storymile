package software.ralf.storymile.user

import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped

internal class GuestUser : User, Scoped {
  private var scopeOrNull: Scope? = null

  override val id: String = "guest"
  override val isGuest: Boolean = true

  override val scope: Scope
    get() = checkNotNull(scopeOrNull) { "The guest user scope is not active." }

  override fun onEnterScope(scope: Scope) {
    check(scopeOrNull == null) { "The guest user already has a scope." }
    scopeOrNull = scope
  }

  override fun onExitScope() {
    scopeOrNull = null
  }
}
