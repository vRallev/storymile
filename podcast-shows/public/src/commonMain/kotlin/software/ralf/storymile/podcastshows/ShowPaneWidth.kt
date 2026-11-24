package software.ralf.storymile.podcastshows

import androidx.compose.ui.unit.Dp
import software.ralf.storymile.design.supportsListDetailPanes

/** True when this content width can fit two show panes at the current [fontScale]. */
fun Dp.supportsShowPanes(fontScale: Float): Boolean = supportsListDetailPanes(fontScale)
