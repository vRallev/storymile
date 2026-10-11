package software.ralf.storymile.storage

import dev.zacsweers.metro.Qualifier

/**
 * Selects the persistent backend for runtime-mode settings. Feature data must use the
 * scope-qualified [Storage] or unqualified [ScopedStorage.Factory] so fake mode keeps it in memory.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PersistentStorage
