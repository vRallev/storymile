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
  showAvailable: Boolean,
  detailRequested: Boolean,
  detail: @Composable (split: Boolean) -> Unit,
  content: @Composable (split: Boolean) -> Unit,
) {
  val window = LocalWindowInfo.current.containerDpSize
  val info = currentWindowAdaptiveInfoV2()
  val directive = calculatePaneScaffoldDirective(info)
  val availableWidth = width / LocalDensity.current.fontScale
  val split =
    showAvailable &&
      availableWidth >= 720.dp &&
      !info.windowPosture.isTabletop &&
      (window.width >= window.height || directive.excludedBounds.isNotEmpty())
  val showDetail = split || (showAvailable && detailRequested)
  val showList = split || !showDetail
  val paneGap = if (split) 12.dp else 0.dp
  ListDetailPaneScaffold(
    directive =
      directive.copy(
        maxHorizontalPartitions = if (split) 2 else 1,
        horizontalPartitionSpacerSize = paneGap,
      ),
    value =
      ThreePaneScaffoldValue(
        primary = paneValue(showDetail),
        secondary = paneValue(showList),
        tertiary = PaneAdaptedValue.Hidden,
      ),
    listPane = {
      if (showList) {
        Box(Modifier.preferredWidth(0.5f).fillMaxSize().clipToBounds()) { content(split) }
      }
    },
    detailPane = {
      if (showDetail) {
        Box(
          Modifier.preferredWidth(0.5f).fillMaxSize().clipToBounds().padding(top = paneGap * 2),
        ) {
          detail(split)
        }
      }
    },
  )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun paneValue(visible: Boolean): PaneAdaptedValue =
  if (visible) PaneAdaptedValue.Expanded else PaneAdaptedValue.Hidden
