package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

/** Selects one backend for app and user data after the startup mode is known. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RuntimeModeStoragePlatform(
  @PersistentStorage private val persistent: StoragePlatform,
  private val runtimeModeController: RuntimeModeController,
) : StoragePlatform() {
  private val inMemory = InMemoryStoragePlatform()

  override suspend fun createPreferences(
    namespace: String,
    name: String,
    coroutineScope: CoroutineScope,
  ): DataStore<Preferences> = platform().createPreferences(namespace, name, coroutineScope)

  override suspend fun readFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
  ): ByteArray? = platform().readFile(namespace, area, name)

  override suspend fun writeFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
    content: ByteArray,
  ) = platform().writeFile(namespace, area, name, content)

  override suspend fun deleteFile(namespace: String, area: FileStorageArea, name: String) =
    platform().deleteFile(namespace, area, name)

  override suspend fun deleteAllFiles(namespace: String, area: FileStorageArea, path: String) =
    platform().deleteAllFiles(namespace, area, path)

  private suspend fun platform(): StoragePlatform =
    when (runtimeModeController.mode()) {
      RuntimeMode.Real -> persistent
      RuntimeMode.Fake -> inMemory
    }
}
