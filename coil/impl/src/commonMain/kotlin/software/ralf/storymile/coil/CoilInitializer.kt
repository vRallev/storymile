package software.ralf.storymile.coil

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import okio.IOException
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.storymile.storage.Storage

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class CoilInitializer(@ForScope(AppScope::class) private val storage: Storage) : Scoped {
  override fun onEnterScope(scope: Scope) {
    val directory =
      try {
        storage.cache.directory("coil").path
      } catch (_: IOException) {
        null
      }
    SingletonImageLoader.setSafe { context ->
      ImageLoader.Builder(context)
        .diskCache { directory?.let { DiskCache.Builder().directory(it).build() } }
        .build()
    }
  }
}
