package software.ralf.storymile.storage

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import software.ralf.app.platform.scope.coroutine.IoCoroutineDispatcher

/** Stores durable data in Application Support and cache files in Caches within the app sandbox. */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<StoragePlatform>())
class IosStoragePlatform(@IoCoroutineDispatcher ioDispatcher: CoroutineDispatcher) :
  OkioStoragePlatform(FileSystem.SYSTEM, ioDispatcher) {
  override fun rootDirectory(): Path {
    val applicationSupportDirectory =
      NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
      )
    return requireNotNull(applicationSupportDirectory?.path).toPath() / "Storymile" / "storage"
  }

  override fun cacheDirectory(): Path {
    val cachesDirectory =
      NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
      )
    return requireNotNull(cachesDirectory?.path).toPath() / "Storymile" / "storage"
  }
}
