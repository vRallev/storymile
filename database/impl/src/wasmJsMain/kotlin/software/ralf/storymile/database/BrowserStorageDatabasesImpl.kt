package software.ralf.storymile.database

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.storymile.storage.BrowserStorageDatabases

@Inject
@ContributesBinding(AppScope::class)
class BrowserStorageDatabasesImpl : BrowserStorageDatabases {
  override suspend fun hasDatabases(encodedNamespace: String): Boolean =
    PlatformDatabase.manageFiles(encodedNamespace, delete = false)

  override suspend fun deleteDatabases(encodedNamespace: String) {
    PlatformDatabase.manageFiles(encodedNamespace, delete = true)
  }
}
