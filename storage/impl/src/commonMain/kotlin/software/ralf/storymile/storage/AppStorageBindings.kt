package software.ralf.storymile.storage

import dev.zacsweers.metro.AppScope
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

@ContributesTo(AppScope::class)
@BindingContainer
interface AppStorageBindings {
  @Binds
  @ForScope(AppScope::class)
  fun storage(@ForStorageLifecycle instance: ScopedStorage): Storage

  companion object {
    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun scoped(@ForStorageLifecycle instance: suspend () -> ScopedStorage): Scoped =
      object : Scoped {
        override fun onEnterScope(scope: Scope) {
          scope.launch { scope.register(instance()) }
        }
      }

    @Provides
    @SingleIn(AppScope::class)
    @ForStorageLifecycle
    suspend fun appStorage(
      factory: ScopedStorage.Factory,
      @ForScope(AppScope::class) coroutineScope: CoroutineScope,
    ): ScopedStorage = factory.create("app", coroutineScope)
  }

  // Storage injection and lifecycle registration share the same scoped suspend binding.
  @Qualifier @Retention(AnnotationRetention.BINARY) private annotation class ForStorageLifecycle
}
