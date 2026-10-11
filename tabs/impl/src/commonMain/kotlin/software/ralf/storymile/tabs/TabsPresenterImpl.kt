package software.ralf.storymile.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.storymile.home.HomePresenter
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class TabsPresenterImpl(private val homePresenter: HomePresenter<*>) : TabsPresenter {
  @Composable
  override fun present(input: Unit): TabsPresenter.Model {
    var selectedTab by remember { mutableStateOf(TabsPresenter.Tab.HOME) }
    return TabsPresenter.Model(
      selectedTab = selectedTab,
      content =
        when (selectedTab) {
          TabsPresenter.Tab.HOME -> homePresenter.present(Unit)
          TabsPresenter.Tab.LIBRARY,
          TabsPresenter.Tab.DOWNLOADS -> ContentModel(selectedTab)
        },
      onSelectTab = { selectedTab = it },
    )
  }

  // Temporary content model until each tab has its own presenter and renderer.
  data class ContentModel(val selectedTab: TabsPresenter.Tab) : BaseModel
}
