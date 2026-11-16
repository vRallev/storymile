package software.ralf.storymile.librarysaved

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.compositionLocalOf

/** Lets Library preserve the Saved scroll position across layouts and navigation. */
val LocalLibrarySavedListState = compositionLocalOf<LazyListState?> { null }
