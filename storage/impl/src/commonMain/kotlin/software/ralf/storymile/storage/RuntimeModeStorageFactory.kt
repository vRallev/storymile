package software.ralf.storymile.storage

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import software.ralf.storymile.runtimemode.RuntimeModeController

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RuntimeModeStorageFactory(
  @PersistentStorage private val persistentFactory: ScopedStorage.Factory,
  private val runtimeModeController: RuntimeModeController,
) : ScopedStorage.Factory {
  private val memoryFactory = InMemoryStorageFactory()

  override fun create(namespace: String, coroutineScope: CoroutineScope): ScopedStorage =
    wrap(namespace, coroutineScope, persistentFactory.create(namespace, coroutineScope.child()))

  fun wrap(
    namespace: String,
    coroutineScope: CoroutineScope,
    persistent: ScopedStorage,
  ): ScopedStorage =
    RuntimeModeStorage(
      persistent = persistent,
      memory = memoryFactory.create(namespace, coroutineScope.child()),
      coroutineScope = coroutineScope,
      runtimeModeController = runtimeModeController,
    )

  private fun CoroutineScope.child(): CoroutineScope =
    CoroutineScope(coroutineContext + Job(checkNotNull(coroutineContext[Job])))
}
