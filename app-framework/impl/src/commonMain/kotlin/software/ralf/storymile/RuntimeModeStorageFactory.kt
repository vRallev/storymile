package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.modeImplementation
import software.ralf.storymile.storage.DefaultStorage
import software.ralf.storymile.storage.InMemoryStorageFactory
import software.ralf.storymile.storage.ScopedStorage

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DefaultStorage.Factory::class])
@Inject
class RuntimeModeStorageFactory(
  private val realFactory: DefaultStorage.Factory,
  private val runtimeModeController: RuntimeModeController,
) : ScopedStorage.Factory {
  private val fakeFactory = InMemoryStorageFactory()

  override fun create(namespace: String, coroutineScope: CoroutineScope): ScopedStorage =
    DeferredStorage(coroutineScope) {
      runtimeModeController.awaitInitialization()
      runtimeModeController.modeImplementation(
        realImpl = { realFactory.create(namespace, coroutineScope) },
        fakeImpl = { fakeFactory.create(namespace, coroutineScope) },
      )
    }
}
