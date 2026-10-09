package software.ralf.storymile.tabs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.tabs.impl.generated.resources.Res
import software.ralf.storymile.tabs.impl.generated.resources.downloads
import software.ralf.storymile.tabs.impl.generated.resources.home
import software.ralf.storymile.tabs.impl.generated.resources.library
import software.ralf.storymile.tabs.impl.generated.resources.storymile
import software.ralf.storymile.tabs.impl.generated.resources.storymile_logo
import software.ralf.storymile.templates.LocalTabPlacement
import software.ralf.storymile.templates.TabPlacement
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.appLayerShadow

@ContributesRenderer
class TabsRenderer : ComposeRenderer<TabsPresenter.Model>() {
  @Composable
  @OptIn(ExperimentalMaterial3Api::class)
  override fun Compose(model: TabsPresenter.Model, modifier: Modifier) {
    val colors = AppTheme.colorScheme
    val insets = WindowInsets.safeDrawing
    val containerModifier = modifier.testTag("tabs")
    when (LocalTabPlacement.current) {
      TabPlacement.BOTTOM -> {
        val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        NavigationBar(
          modifier = containerModifier.fillMaxWidth().appLayerShadow(shape).clip(shape),
          containerColor = colors.surface,
          tonalElevation = 0.dp,
          windowInsets = insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        ) {
          TabsPresenter.Tab.entries.forEach { tab ->
            val title = tab.label()
            val tooltipState = rememberTooltipState()
            Row(Modifier.weight(1f)) {
              TooltipBox(
                positionProvider =
                  TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(title) } },
                state = remember(tooltipState) { HoverTooltipState(tooltipState) },
              ) {
                NavigationBarItem(
                  selected = model.selectedTab == tab,
                  onClick = { model.onSelectTab(tab) },
                  icon = {
                    Box(
                      modifier = Modifier.fillMaxWidth(0.5f),
                      contentAlignment = Alignment.Center,
                    ) {
                      TabIcon(
                        tab = tab,
                        selected = model.selectedTab == tab,
                        modifier = Modifier.size(32.dp),
                      )
                    }
                  },
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(horizontal = 4.dp)
                      .testTag("tab-${tab.name.lowercase()}")
                      .semantics { contentDescription = title },
                )
              }
            }
          }
        }
      }
      TabPlacement.START ->
        NavigationRail(
          modifier = containerModifier.fillMaxHeight(),
          containerColor = colors.surfaceContainer,
          windowInsets = insets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
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
          windowInsets = insets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
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
    val content: @Composable () -> Unit = {
      Image(
        painter = painterResource(Res.drawable.storymile_logo),
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
  private fun TabIcon(
    tab: TabsPresenter.Tab,
    selected: Boolean,
    modifier: Modifier = Modifier,
  ) {
    Crossfade(
      targetState = selected,
      modifier = modifier.size(24.dp),
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
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }

  @Composable
  private fun TabsPresenter.Tab.label(): String =
    stringResource(
      when (this) {
        TabsPresenter.Tab.HOME -> Res.string.home
        TabsPresenter.Tab.LIBRARY -> Res.string.library
        TabsPresenter.Tab.DOWNLOADS -> Res.string.downloads
      },
    )

  @OptIn(ExperimentalMaterial3Api::class)
  private class HoverTooltipState(private val state: TooltipState) : TooltipState by state {
    private var hoverJob: Job? = null

    override suspend fun show(mutatePriority: MutatePriority) {
      hoverJob?.cancel()
      if (mutatePriority == MutatePriority.UserInput) {
        // Material uses UserInput for mouse hover and PreventUserInput for touch long press.
        val job = currentCoroutineContext().job
        hoverJob = job
        try {
          delay(500)
          state.show(mutatePriority)
        } finally {
          if (hoverJob == job) {
            hoverJob = null
          }
        }
      } else {
        hoverJob = null
        state.show(mutatePriority)
      }
    }

    override fun dismiss() {
      hoverJob?.cancel()
      state.dismiss()
    }

    override fun onDispose() {
      hoverJob?.cancel()
      state.onDispose()
    }
  }
}
