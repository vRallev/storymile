package software.ralf.storymile.storage

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@ContributesTo(AppScope::class)
@BindingContainer
interface AppStorageBindings {
  companion object {
    @Provides
    @SingleIn(AppScope::class)
    @ForScope(AppScope::class)
    fun storage(
      factory: Storage.Factory,
      @ForScope(AppScope::class) coroutineScope: CoroutineScope,
    ): Storage = factory.create("app", coroutineScope)
  }
}
