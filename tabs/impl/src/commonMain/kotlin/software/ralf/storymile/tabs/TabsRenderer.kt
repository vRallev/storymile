package software.ralf.storymile.tabs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.tabs.impl.generated.resources.Res
import software.ralf.storymile.tabs.impl.generated.resources.downloads
import software.ralf.storymile.tabs.impl.generated.resources.home
import software.ralf.storymile.tabs.impl.generated.resources.library
import software.ralf.storymile.tabs.impl.generated.resources.storymile
import software.ralf.storymile.tabs.impl.generated.resources.storymile_logo_dark
import software.ralf.storymile.tabs.impl.generated.resources.storymile_logo_light
import software.ralf.storymile.templates.LocalTabPlacement
import software.ralf.storymile.templates.TabPlacement
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.appLayerShadow

@ContributesRenderer
class TabsRenderer : ComposeRenderer<TabsPresenter.Model>() {
  @Composable
  override fun Compose(model: TabsPresenter.Model, modifier: Modifier) {
    val colors = AppTheme.colorScheme
    val insets = WindowInsets(0, 0, 0, 0)
    val containerModifier = modifier.testTag("tabs")
    when (LocalTabPlacement.current) {
      TabPlacement.BOTTOM -> {
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        NavigationBar(
          modifier = containerModifier.fillMaxWidth().appLayerShadow(shape).clip(shape),
          containerColor = colors.surface,
          tonalElevation = 0.dp,
          windowInsets = insets,
        ) {
          TabsPresenter.Tab.entries.forEach { tab ->
            NavigationBarItem(
              selected = model.selectedTab == tab,
              onClick = { model.onSelectTab(tab) },
              icon = { TabIcon(tab, model.selectedTab == tab) },
              label = { Text(tab.label()) },
              modifier = Modifier.padding(horizontal = 4.dp).testTag("tab-${tab.name.lowercase()}"),
            )
          }
        }
      }
      TabPlacement.START ->
        NavigationRail(
          modifier = containerModifier.fillMaxHeight(),
          containerColor = colors.surfaceContainer,
          windowInsets = insets,
          header = { Brand(expanded = false) },
        ) {
          TabsPresenter.Tab.entries.forEach { tab ->
            NavigationRailItem(
              selected = model.selectedTab == tab,
              onClick = { model.onSelectTab(tab) },
              icon = { TabIcon(tab, model.selectedTab == tab) },
              label = { Text(tab.label()) },
              modifier = Modifier.padding(vertical = 4.dp).testTag("tab-${tab.name.lowercase()}"),
            )
          }
        }
      TabPlacement.START_EXPANDED ->
        PermanentDrawerSheet(
          modifier = containerModifier.fillMaxHeight(),
          drawerContainerColor = colors.surfaceContainer,
          drawerTonalElevation = 0.dp,
          windowInsets = insets,
        ) {
          Column(Modifier.verticalScroll(rememberScrollState())) {
            Brand(expanded = true)
            Spacer(Modifier.height(16.dp))
            TabsPresenter.Tab.entries.forEach { tab ->
              NavigationDrawerItem(
                label = { Text(tab.label()) },
                selected = model.selectedTab == tab,
                onClick = { model.onSelectTab(tab) },
                icon = { TabIcon(tab, model.selectedTab == tab) },
                modifier =
                  Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    .padding(vertical = 4.dp)
                    .testTag("tab-${tab.name.lowercase()}"),
              )
            }
          }
        }
    }
  }

  @Composable
  private fun Brand(expanded: Boolean) {
    val logo =
      if (AppTheme.colorScheme.surfaceContainer.luminance() < 0.5f) {
        Res.drawable.storymile_logo_dark
      } else {
        Res.drawable.storymile_logo_light
      }
    val content: @Composable () -> Unit = {
      Image(
        painter = painterResource(logo),
        contentDescription = null,
        modifier = Modifier.size(80.dp).testTag("storymile-logo"),
      )
      Text(
        text = stringResource(Res.string.storymile),
        style = if (expanded) AppTheme.typography.titleLarge else AppTheme.typography.titleMedium,
        color = AppTheme.colorScheme.onSurface,
      )
    }
    if (expanded) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
      )
    } else {
      Column(
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = { content() },
      )
    }
  }

  @Composable
  private fun TabIcon(tab: TabsPresenter.Tab, selected: Boolean) {
    Crossfade(
      targetState = selected,
      modifier = Modifier.size(24.dp),
      animationSpec = tween(durationMillis = 120),
      label = "tab-icon",
    ) { filled ->
      val icon =
        when (tab) {
          TabsPresenter.Tab.HOME -> if (filled) Icons.Filled.Home else Icons.Outlined.Home
          TabsPresenter.Tab.LIBRARY ->
            if (filled) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
          TabsPresenter.Tab.DOWNLOADS ->
            if (filled) Icons.Filled.Download else Icons.Outlined.Download
        }
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
    }
  }

  @Composable
  private fun TabsPresenter.Tab.label(): String =
    stringResource(
      when (this) {
        TabsPresenter.Tab.HOME -> Res.string.home
        TabsPresenter.Tab.LIBRARY -> Res.string.library
        TabsPresenter.Tab.DOWNLOADS -> Res.string.downloads
      }
    )
}
