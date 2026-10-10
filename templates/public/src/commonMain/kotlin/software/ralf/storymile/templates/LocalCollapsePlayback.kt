package software.ralf.storymile.templates

import androidx.compose.runtime.staticCompositionLocalOf

/** Returns expanded playback to its bar. Available inside the expanded playback slot. */
val LocalCollapsePlayback = staticCompositionLocalOf<() -> Unit> { {} }
