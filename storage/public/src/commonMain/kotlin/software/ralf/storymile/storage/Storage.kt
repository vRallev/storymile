package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope

/**
 * Scope-owned preferences, durable files, and caches. Inject with `@ForScope(AppScope::class)` for
 * app data. Each namespace has separate preferences, files, and caches. Scope exit stops access and
 * preserves saved data.
 *
 * Fake mode keeps preferences, files, and caches in memory until the app restarts. The runtime mode
 * preference stays persistent. In real mode, Android, Desktop, and iOS use DataStore files:
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

  /**
   * Returns a stable database path in this scope, or null for in-memory storage. Names contain only
   * letters, digits, underscores, and hyphens. Native platforms create the parent directory; the
   * browser returns a virtual filename for its SQLite filesystem. The caller owns the database.
   */
  suspend fun databasePath(name: String): String?

  /** Creates storage for an owning coroutine scope. */
  interface Factory {
    /**
     * Creates storage ready for use. The first operation selects the backend and claims the
     * namespace.
     *
     * [namespace] contains only letters, digits, underscores, and hyphens. Use `app` for app data
     * and a stable `user-<id>` for each user. An active namespace cannot be shared. Reopening waits
     * for the previous owner's storage work and cleanup to finish.
     *
     * [coroutineScope] must be a dedicated child of the owning scope. The caller cancels it to stop
     * storage access while preserving saved data.
     *
     * When [mustBePersistent] is true, use persistent storage without checking the runtime mode.
     * Otherwise, fake mode uses in-memory storage.
     */
    fun create(
      namespace: String,
      coroutineScope: CoroutineScope,
      mustBePersistent: Boolean = false,
    ): Storage
  }
}
