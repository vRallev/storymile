package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/**
 * Scope-owned preferences, durable files, and caches. Inject with `@ForScope(AppScope::class)` for
 * app data. Each namespace has separate preferences, files, and caches. Scope exit stops access and
 * preserves saved data.
 *
 * Android, Desktop, and iOS store preferences in DataStore files:
 * - Android: preferences and files use private `filesDir`; caches use `cacheDir`.
 * - Desktop: preferences and files use `~/.storymile/storage`; caches use the OS cache directory.
 * - iOS: preferences and files use Application Support; caches use Caches in the app sandbox.
 *
 * Wasm stores preferences in browser local storage. Files and caches use separate directories in
 * the browser's origin-private filesystem. Browser storage is isolated by origin and subject to
 * browser quotas and eviction.
 */
interface Storage {
  /** Durable files in this scope's directory. */
  val files: FileStorage

  /**
   * Temporary files isolated from [files]. The system can remove cached data; recreate it as
   * needed.
   */
  val cache: FileStorage

  /**
   * Returns a named preferences store. Names contain only letters, digits, underscores, and
   * hyphens. Preference keys belong to the caller. Repeated calls return the same store.
   */
  suspend fun preferences(name: String): DataStore<Preferences>
}
