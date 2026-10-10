package software.ralf.storymile.storage

import kotlinx.coroutines.CoroutineScope
import software.ralf.app.platform.scope.Scoped

/**
 * Storage with lifecycle callbacks for scope assembly. Register this instance in its owning scope's
 * lifecycle set. Feature consumers should inject [Storage].
 */
interface ScopedStorage : Storage, Scoped {
  /** Creates storage for a scope's binding container without an implementation dependency. */
  interface Factory {
    /**
     * Creates a new, unregistered instance. Bind the same instance as qualified [Storage] and in
     * the owning scope's lifecycle set before use.
     *
     * [namespace] is a stable identity containing only letters, digits, underscores, and hyphens.
     * Use `app` for app data and a unique `user-<id>` for each user. An active namespace cannot be
     * shared. Reopening waits for the previous owner's storage work to finish.
     *
     * [coroutineScope] must be a dedicated child of the owning scope. Storage cancels it on exit.
     */
    fun create(namespace: String, coroutineScope: CoroutineScope): ScopedStorage
  }
}
