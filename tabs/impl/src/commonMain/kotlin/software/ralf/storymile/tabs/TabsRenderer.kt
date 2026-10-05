package software.ralf.storymile.tabs

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val colors = AppTheme.colorScheme
    val insets = WindowInsets(0, 0, 0, 0)
    val containerModifier = modifier.testTag("tabs")
    when (LocalTabPlacement.current) {
      TabPlacement.BOTTOM -> {
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        NavigationBar(
          modifier =
            containerModifier.fillMaxWidth().height(80.dp).appLayerShadow(shape).clip(shape),
          containerColor = colors.surface,
          tonalElevation = 0.dp,
          windowInsets = insets,
        ) {}
      }
      TabPlacement.START ->
        NavigationRail(
          modifier = containerModifier.width(96.dp).fillMaxHeight(),
          containerColor = colors.surfaceContainer,
          windowInsets = insets,
        ) {}
      TabPlacement.START_EXPANDED ->
        PermanentDrawerSheet(
          modifier = containerModifier.width(280.dp).fillMaxHeight(),
          drawerContainerColor = colors.surfaceContainer,
          drawerTonalElevation = 0.dp,
          windowInsets = insets,
        ) {}
    }
  }
}
