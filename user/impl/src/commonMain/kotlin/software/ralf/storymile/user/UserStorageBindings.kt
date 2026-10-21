package software.ralf.storymile.user

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.storymile.storage.ScopedStorage
import software.ralf.storymile.storage.Storage

@BindingContainer
@ContributesTo(UserScope::class)
interface UserStorageBindings {
  @Binds
  @ForScope(UserScope::class)
  fun storage(@ForStorageLifecycle instance: ScopedStorage): Storage

  companion object {
    @Provides
    @IntoSet
    @ForScope(UserScope::class)
    fun scoped(@ForStorageLifecycle instance: suspend () -> ScopedStorage): Scoped =
      object : Scoped {
        override fun onEnterScope(scope: Scope) {
          scope.launch { scope.register(instance()) }
        }
      }

    @Provides
    @SingleIn(UserScope::class)
    @ForStorageLifecycle
    suspend fun userStorage(
      user: User,
      factory: ScopedStorage.Factory,
      @ForScope(UserScope::class) coroutineScope: CoroutineScope,
    ): ScopedStorage = factory.create("user-${user.id}", coroutineScope)
  }

  // Storage injection and lifecycle registration share the same scoped suspend binding.
  @Qualifier @Retention(AnnotationRetention.BINARY) private annotation class ForStorageLifecycle
}
