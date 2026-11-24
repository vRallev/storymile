package software.ralf.storymile.storage

/**
 * Stores preferences, files, and caches in memory. Each factory starts empty. Data survives scope
 * exit within this factory, but is never written to disk or browser storage.
 */
class InMemoryStorageFactory : Storage.Factory by DefaultStorage.Factory(InMemoryStoragePlatform())
