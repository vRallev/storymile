package software.ralf.storymile.storage

import dev.zacsweers.metro.Qualifier

/** Identifies the platform backend before runtime mode selects a storage backend. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PersistentStorage
