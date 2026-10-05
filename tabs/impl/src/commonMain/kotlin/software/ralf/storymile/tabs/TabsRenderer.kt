package software.ralf.storymile.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.templates.LocalTabPlacement
import software.ralf.storymile.templates.TabPlacement
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.appLayerShadow

@ContributesRenderer
class TabsRenderer : ComposeRenderer<TabsPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: TabsPresenterImpl.Model, modifier: Modifier) {
    val placement = LocalTabPlacement.current
    val atBottom = placement == TabPlacement.BOTTOM
    val expanded = placement == TabPlacement.START_EXPANDED
    val shape =
      if (atBottom) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else RectangleShape
    Surface(
      modifier =
        modifier
          .then(
            if (atBottom) Modifier.fillMaxWidth().height(80.dp).appLayerShadow(shape)
            else Modifier.width(if (expanded) 280.dp else 96.dp).fillMaxHeight()
          )
          .testTag("tabs"),
      shape = shape,
      color = if (atBottom) AppTheme.colorScheme.surface else AppTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {}
  }
}
