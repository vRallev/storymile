package software.ralf.storymile.storage

import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.coroutineScope

internal suspend fun Scope.storage(namespace: String, platform: StoragePlatform): Storage {
  return DefaultStorage.create(namespace, coroutineScope(), platform).also { register(it) }
}
