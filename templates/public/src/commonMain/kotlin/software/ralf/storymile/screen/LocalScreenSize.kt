package software.ralf.storymile.screen

import androidx.compose.runtime.compositionLocalOf

/** Current adaptive screen classification reported by the application template renderer. */
val LocalScreenSize = compositionLocalOf { ScreenSize.Zero }
