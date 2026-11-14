package software.ralf.storymile.podcastshows

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True when this content width can fit two show panes at the current [fontScale]. */
fun Dp.supportsShowPanes(fontScale: Float): Boolean = this / fontScale >= 760.dp
