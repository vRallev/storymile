package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import software.ralf.storymile.storage.InMemoryStorageFactory
import software.ralf.storymile.storage.ScopedStorage

/** Gives each integration-test graph its own in-memory storage. */
@ContributesTo(AppScope::class)
@BindingContainer
interface TestStorageBindings {
  companion object {
    @Provides
    @SingleIn(AppScope::class)
    fun storageFactory(): ScopedStorage.Factory = InMemoryStorageFactory()
  }
}
