package software.ralf.storymile.design

import androidx.compose.runtime.staticCompositionLocalOf

/** Hidden action for [StorymileLogo]. Defaults to no action. */
val LocalOnLogoLongPress = staticCompositionLocalOf<() -> Unit> { {} }
