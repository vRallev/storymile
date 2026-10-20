package software.ralf.storymile.episodedetails

import androidx.compose.runtime.compositionLocalOf

/** Omits the screen title bar when the caller supplies navigation around an episode pane. */
val LocalEpisodeDetailsPane = compositionLocalOf { false }
