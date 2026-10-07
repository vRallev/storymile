package software.ralf.storymile.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel

@Inject
@ContributesBinding(AppScope::class)
class TabsPresenterImpl : TabsPresenter {
  @Composable
  override fun present(input: Unit): TabsPresenter.Model {
    var selectedTab by remember { mutableStateOf(TabsPresenter.Tab.HOME) }
    return TabsPresenter.Model(
      selectedTab = selectedTab,
      content = ContentModel(selectedTab),
      onSelectTab = { selectedTab = it },
    )
  }

  // Temporary content model until each tab has its own presenter and renderer.
  data class ContentModel(val selectedTab: TabsPresenter.Tab) : BaseModel
}
