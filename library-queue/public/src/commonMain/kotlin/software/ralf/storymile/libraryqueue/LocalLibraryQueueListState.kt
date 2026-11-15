package software.ralf.storymile.libraryqueue

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.compositionLocalOf

/** Lets Library preserve the Queue scroll position across layouts and navigation. */
val LocalLibraryQueueListState = compositionLocalOf<LazyListState?> { null }
