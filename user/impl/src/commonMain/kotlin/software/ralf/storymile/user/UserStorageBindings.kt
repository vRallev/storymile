package software.ralf.storymile.user

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import software.ralf.storymile.storage.Storage

@BindingContainer
@ContributesTo(UserScope::class)
interface UserStorageBindings {
  companion object {
    @Provides
    @SingleIn(UserScope::class)
    @ForScope(UserScope::class)
    fun storage(
      user: User,
      factory: Storage.Factory,
      @ForScope(UserScope::class) coroutineScope: CoroutineScope,
    ): Storage = factory.create("user-${user.id}", coroutineScope)
  }
}
