package software.ralf.storymile.runtimemode

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
import software.ralf.storymile.storage.ScopedStorage
import software.ralf.storymile.storage.Storage

@BindingContainer
@ContributesTo(AppScope::class)
interface RuntimeModeStorageBindings {
  @Binds @ForRuntimeModeStorage fun storage(@ForRuntimeModeStorage instance: ScopedStorage): Storage

  companion object {
    @Provides
    @SingleIn(AppScope::class)
    @ForRuntimeModeStorage
    suspend fun scopedStorage(
      factory: ScopedStorage.Factory,
      @ForScope(AppScope::class) coroutineScope: CoroutineScope,
    ): ScopedStorage = factory.create("runtime-mode", coroutineScope, mustBePersistent = true)

    @Provides
    @IntoSet
    @ForScope(AppScope::class)
    fun scoped(@ForRuntimeModeStorage storage: suspend () -> ScopedStorage): Scoped =
      object : Scoped {
        override fun onEnterScope(scope: Scope) {
          scope.launch { scope.register(storage()) }
        }
      }
  }

  @Qualifier @Retention(AnnotationRetention.BINARY) annotation class ForRuntimeModeStorage
}
