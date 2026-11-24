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

  private val activeFactory: Storage.Factory
    get() = if (controller.value.mode == RuntimeMode.Real) real else memory

  override suspend fun hasNamespace(namespace: String): Boolean =
    activeFactory.hasNamespace(namespace)

  override suspend fun copyPreferences(sourceNamespace: String, targetNamespace: String) {
    activeFactory.copyPreferences(
      sourceNamespace = sourceNamespace,
      targetNamespace = targetNamespace,
    )
  }

  override suspend fun deleteNamespace(namespace: String) {
    activeFactory.deleteNamespace(namespace)
  }

  override fun create(
    namespace: String,
    coroutineScope: CoroutineScope,
    mustBePersistent: Boolean,
  ): Storage {
    val factory = if (mustBePersistent) real else activeFactory
    return factory.create(
      namespace = namespace,
      coroutineScope = coroutineScope,
      mustBePersistent = mustBePersistent,
    )
  }
}
