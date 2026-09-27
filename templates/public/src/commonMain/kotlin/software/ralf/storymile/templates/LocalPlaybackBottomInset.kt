package software.ralf.storymile.templates

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Space occupied by bottom tabs within the playback layer. Playback renderers extend their
 * background into this inset and keep their controls above it.
 */
val LocalPlaybackBottomInset = compositionLocalOf { 0.dp }
