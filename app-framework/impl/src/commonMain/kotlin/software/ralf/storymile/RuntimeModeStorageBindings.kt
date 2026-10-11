package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.ScopedStorage

@ContributesTo(AppScope::class)
@BindingContainer
interface RuntimeModeStorageBindings {
  companion object {
    @Provides
    @SingleIn(AppScope::class)
    @ForScope(RuntimeModeController::class)
    fun storage(
      factory: DefaultStorage.Factory,
      @ForScope(AppScope::class) coroutineOwner: CoroutineScopeScoped,
    ): ScopedStorage = factory.create("runtime-mode", coroutineOwner.createChild())
  }
}
