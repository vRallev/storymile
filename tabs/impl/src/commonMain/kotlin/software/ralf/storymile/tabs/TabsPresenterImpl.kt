package software.ralf.storymile.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.storymile.downloads.DownloadsPresenter
import software.ralf.storymile.home.HomePresenter
import software.ralf.storymile.library.LibraryPresenter
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class TabsPresenterImpl(
  private val homePresenter: HomePresenter<*>,
  private val libraryPresenter: LibraryPresenter<*>,
  private val downloadsPresenter: () -> DownloadsPresenter<*>,
) : TabsPresenter {
  @Composable
  override fun present(input: Unit): TabsPresenter.Model {
    var selectedTab by remember { mutableStateOf(TabsPresenter.Tab.HOME) }
    val library = libraryPresenter.present(Unit)
    return TabsPresenter.Model(
      selectedTab = selectedTab,
      content =
        when (selectedTab) {
          TabsPresenter.Tab.HOME -> homePresenter.present(Unit)
          TabsPresenter.Tab.LIBRARY -> library
          TabsPresenter.Tab.DOWNLOADS -> remember { downloadsPresenter() }.present(Unit)
        },
      onSelectTab = { selectedTab = it },
    )
  }
}
