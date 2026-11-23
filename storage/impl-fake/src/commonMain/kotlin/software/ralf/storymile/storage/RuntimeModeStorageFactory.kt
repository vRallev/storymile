package software.ralf.storymile.storage

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DefaultStorage.Factory::class])
class RuntimeModeStorageFactory(
  private val real: DefaultStorage.Factory,
  private val controller: Lazy<RuntimeModeController>,
) : Storage.Factory {
  private val memory by lazy { InMemoryStorageFactory() }

  override fun create(
    namespace: String,
    coroutineScope: CoroutineScope,
    mustBePersistent: Boolean,
  ): Storage {
    val factory =
      if (mustBePersistent || controller.value.mode == RuntimeMode.Real) real else memory
    return factory.create(
      namespace = namespace,
      coroutineScope = coroutineScope,
      mustBePersistent = mustBePersistent,
    )
  }
}
