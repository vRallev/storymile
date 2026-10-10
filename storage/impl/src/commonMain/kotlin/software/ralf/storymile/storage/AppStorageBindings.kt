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
import software.ralf.app.platform.scope.Scoped

@ContributesTo(AppScope::class)
@BindingContainer
interface AppStorageBindings {
  @Binds
  @ForScope(AppScope::class)
  fun storage(@ForStorageLifecycle instance: ScopedStorage): Storage

  @Binds
  @IntoSet
  @ForScope(AppScope::class)
  fun scoped(@ForStorageLifecycle instance: ScopedStorage): Scoped

  companion object {
    @Provides
    @SingleIn(AppScope::class)
    @ForStorageLifecycle
    fun appStorage(
      factory: ScopedStorage.Factory,
      @ForScope(AppScope::class) coroutineScope: CoroutineScope,
    ): ScopedStorage = factory.create("app", coroutineScope)
  }

  // A private qualifier hides this ScopedStorage binding from consumer injection. Both aliases use
  // this key so Storage and scope registration share the same instance.
  @Qualifier @Retention(AnnotationRetention.BINARY) private annotation class ForStorageLifecycle
}
