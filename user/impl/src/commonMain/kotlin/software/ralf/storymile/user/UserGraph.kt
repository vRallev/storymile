package software.ralf.storymile.user

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.storymile.storage.Storage

@GraphExtension(UserScope::class)
interface UserGraph {
  val user: User

  @ForScope(UserScope::class) val storage: suspend () -> Storage

  @ForScope(UserScope::class) @Multibinds(allowEmpty = true) val userScopedInstances: Set<Scoped>

  @ForScope(UserScope::class) val coroutineOwner: CoroutineScopeScoped

  @GraphExtension.Factory
  @ContributesTo(AppScope::class)
  fun interface Factory {
    fun createUserGraph(@Provides user: User): UserGraph
  }
}
