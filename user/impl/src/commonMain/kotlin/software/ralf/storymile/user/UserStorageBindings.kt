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
      @ForScope(UserScope::class) storageNamespace: String,
      factory: Storage.Factory,
      @ForScope(UserScope::class) coroutineScope: CoroutineScope,
    ): Storage = factory.create(namespace = storageNamespace, coroutineScope = coroutineScope)
  }
}
