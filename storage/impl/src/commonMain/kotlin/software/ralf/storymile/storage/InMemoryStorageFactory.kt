package software.ralf.storymile.storage

import kotlinx.coroutines.CoroutineScope

/**
 * Stores preferences, files, and caches in memory. Each factory starts empty. Data survives scope
 * exit within this factory, but is never written to disk or browser storage.
 */
class InMemoryStorageFactory : ScopedStorage.Factory {
  private val platform = InMemoryStoragePlatform()

  override fun create(namespace: String, coroutineScope: CoroutineScope): ScopedStorage =
    DefaultStorage(namespace, coroutineScope, platform)
}
