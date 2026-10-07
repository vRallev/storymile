package software.ralf.storymile.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.tabs.impl.generated.resources.Res
import software.ralf.storymile.tabs.impl.generated.resources.downloads_selected
import software.ralf.storymile.tabs.impl.generated.resources.home_selected
import software.ralf.storymile.tabs.impl.generated.resources.library_selected
import software.ralf.storymile.theme.AppTheme

// Temporary placeholder until each tab has its own presenter and renderer.
@ContributesRenderer
class TabContentRenderer : ComposeRenderer<TabsPresenterImpl.ContentModel>() {
  @Composable
  override fun Compose(model: TabsPresenterImpl.ContentModel, modifier: Modifier) {
    val message =
      when (model.selectedTab) {
        TabsPresenter.Tab.HOME -> Res.string.home_selected
        TabsPresenter.Tab.LIBRARY -> Res.string.library_selected
        TabsPresenter.Tab.DOWNLOADS -> Res.string.downloads_selected
      }
    Box(modifier.fillMaxSize().testTag("tab-content"), contentAlignment = Alignment.Center) {
      Text(
        text = stringResource(message),
        modifier = Modifier.padding(24.dp).testTag("selected-tab"),
        style = AppTheme.typography.headlineMedium,
        color = AppTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )
    }
  }
}
