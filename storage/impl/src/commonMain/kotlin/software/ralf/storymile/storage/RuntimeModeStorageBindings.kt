package software.ralf.storymile.storage

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import software.ralf.app.platform.scope.Scoped
import software.ralf.storymile.runtimemode.ForRuntimeMode

/** Keeps the saved mode available before ordinary storage can select its backend. */
@BindingContainer
@ContributesTo(AppScope::class)
interface RuntimeModeStorageBindings {
  @Binds @ForRuntimeMode fun storage(@ForRuntimeMode instance: ScopedStorage): Storage

  @Binds
  @IntoSet
  @ForScope(AppScope::class)
  fun scoped(@ForRuntimeMode instance: ScopedStorage): Scoped

  companion object {
    @Provides
    @SingleIn(AppScope::class)
    @ForRuntimeMode
    fun runtimeModeStorage(
      @PersistentStorage platform: StoragePlatform,
      @ForScope(AppScope::class) coroutineScope: CoroutineScope,
    ): ScopedStorage =
      // Preserve the existing app/runtime_mode preference location.
      DefaultStorage("app", coroutineScope, platform)
  }
}
