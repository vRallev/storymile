package software.ralf.storymile.storage

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.scope.coroutine.IoCoroutineDispatcher

/** Stores durable data and cache files in the application's private directories. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<StoragePlatform>())
class AndroidStoragePlatform(
  private val application: Application,
  @IoCoroutineDispatcher ioDispatcher: CoroutineDispatcher,
) : OkioStoragePlatform(FileSystem.SYSTEM, ioDispatcher) {
  override fun rootDirectory(): Path =
    application.filesDir.absolutePath.toPath() / "storymile-storage"

  override fun cacheDirectory(): Path =
    application.cacheDir.absolutePath.toPath() / "storymile-storage"
}
