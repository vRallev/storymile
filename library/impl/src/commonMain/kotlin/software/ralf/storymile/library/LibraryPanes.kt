package software.ralf.storymile.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun LibraryPanes(
  width: Dp,
  showsTabActive: Boolean,
  detailRequested: Boolean,
  detail: @Composable (split: Boolean) -> Unit,
  content: @Composable (split: Boolean) -> Unit,
) {
  val window = LocalWindowInfo.current.containerDpSize
  val info = currentWindowAdaptiveInfoV2()
  val directive = calculatePaneScaffoldDirective(info)
  val split =
    showsTabActive &&
      width / LocalDensity.current.fontScale >= 720.dp &&
      !info.windowPosture.isTabletop &&
      (window.width >= window.height || directive.excludedBounds.isNotEmpty())
  val openDetail = showsTabActive && (split || detailRequested)
  ListDetailPaneScaffold(
    directive =
      directive.copy(
        maxHorizontalPartitions = if (split) 2 else 1,
        horizontalPartitionSpacerSize = if (split) 12.dp else 0.dp,
      ),
    value =
      ThreePaneScaffoldValue(
        primary = if (openDetail) PaneAdaptedValue.Expanded else PaneAdaptedValue.Hidden,
        secondary =
          if (split || !openDetail) PaneAdaptedValue.Expanded else PaneAdaptedValue.Hidden,
        tertiary = PaneAdaptedValue.Hidden,
      ),
    listPane = { Box(Modifier.fillMaxSize().clipToBounds()) { content(split) } },
    detailPane = {
      Box(
        Modifier.fillMaxSize().clipToBounds().padding(top = if (split) 24.dp else 0.dp),
      ) {
        detail(split)
      }
    },
  )
}
