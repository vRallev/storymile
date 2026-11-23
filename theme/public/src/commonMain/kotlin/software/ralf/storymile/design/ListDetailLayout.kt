package software.ralf.storymile.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True when this width fits a list and detail pane at the current [fontScale]. */
fun Dp.supportsListDetailPanes(fontScale: Float): Boolean = this / fontScale >= 760.dp

/**
 * Shows [list] alone or beside [detail] when both panes fit. The list receives its layout modifier
 * and whether the detail pane is visible. While [loading], shows a progress indicator.
 */
@Composable
fun ListDetailLayout(
  loading: Boolean,
  modifier: Modifier = Modifier,
  detail: @Composable () -> Unit,
  list: @Composable (modifier: Modifier, detailVisible: Boolean) -> Unit,
) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    if (loading) {
      CircularProgressIndicator(Modifier.align(Alignment.Center))
    } else {
      val sidePadding = if (maxWidth < 600.dp) 20.dp else 32.dp
      val wide = maxWidth.supportsListDetailPanes(LocalDensity.current.fontScale)
      Row(Modifier.fillMaxSize()) {
        list(Modifier.weight(1.25f).fillMaxHeight().padding(horizontal = sidePadding), wide)
        if (wide) {
          PaneDivider()
          Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
        }
      }
    }
  }
}
