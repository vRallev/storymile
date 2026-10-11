package software.ralf.storymile.runtimemode

import dev.zacsweers.metro.Qualifier

/** Persistent storage reserved for the mode setting, independent of the selected mode. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ForRuntimeMode
