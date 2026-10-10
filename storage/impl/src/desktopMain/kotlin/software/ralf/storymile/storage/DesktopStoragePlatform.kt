package software.ralf.storymile.storage

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.scope.coroutine.IoCoroutineDispatcher
import software.ralf.storymile.util.Platform

/**
 * Stores durable data beneath the user's home directory and cache files in the OS cache directory.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<StoragePlatform>())
class DesktopStoragePlatform(
  @IoCoroutineDispatcher ioDispatcher: CoroutineDispatcher,
  private val platform: Platform,
) : OkioStoragePlatform(FileSystem.SYSTEM, ioDispatcher) {
  override fun rootDirectory(): Path =
    requireNotNull(System.getProperty("user.home")).toPath() / ".storymile" / "storage"

  override fun cacheDirectory(): Path {
    val home = requireNotNull(System.getProperty("user.home")).toPath()
    val cacheRoot =
      when (platform) {
        Platform.Desktop.Mac -> home / "Library" / "Caches" / "Storymile"
        Platform.Desktop.Windows ->
          (environmentDirectory("LOCALAPPDATA") ?: (home / "AppData" / "Local")) /
            "Storymile" /
            "Cache"
        Platform.Desktop.Linux ->
          (environmentDirectory("XDG_CACHE_HOME") ?: (home / ".cache")) / "storymile"
        else -> error("Expected a desktop platform: $platform")
      }
    return cacheRoot / "storage"
  }

  private fun environmentDirectory(name: String): Path? {
    return System.getenv(name)?.takeIf { it.isNotBlank() }?.toPath()?.takeIf { it.isAbsolute }
  }
}
