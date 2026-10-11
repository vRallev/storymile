package software.ralf.storymile.user

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.IoCoroutineDispatcher

@BindingContainer
@ContributesTo(UserScope::class)
interface UserCoroutineBindings {
  companion object {
    @Provides
    @SingleIn(UserScope::class)
    @ForScope(UserScope::class)
    fun coroutineOwner(
      @IoCoroutineDispatcher dispatcher: CoroutineDispatcher,
    ): CoroutineScopeScoped =
      CoroutineScopeScoped(dispatcher + SupervisorJob() + CoroutineName("UserScope"))

    @Provides
    @ForScope(UserScope::class)
    fun coroutineScope(@ForScope(UserScope::class) owner: CoroutineScopeScoped): CoroutineScope =
      owner.createChild()
  }
}
