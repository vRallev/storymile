@file:OptIn(ExperimentalAppPlatform::class)

package software.ralf.storymile.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.ExperimentalAppPlatform
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.downloads.DownloadsPresenter
import software.ralf.storymile.home.HomePresenter
import software.ralf.storymile.library.LibraryPresenter

class TabsPresenterImplTest {
  @Test
  fun `tabs start at home and selection updates the destination`() = runTest {
    val presenter: TabsPresenter =
      TabsPresenterImpl(FakeHomePresenter(), FakeLibraryPresenter()) {
        FakeDownloadsPresenter()
      }

    presenter.test(this) {
      var model = awaitItem()
      assertThat(model.selectedTab).isEqualTo(TabsPresenter.Tab.HOME)
      assertThat(model.content).isEqualTo(HomeModel)

      listOf(TabsPresenter.Tab.DOWNLOADS, TabsPresenter.Tab.LIBRARY, TabsPresenter.Tab.HOME)
        .forEach { tab ->
          model.onSelectTab(tab)
          model = awaitItem()
          assertThat(model.selectedTab).isEqualTo(tab)
          if (tab == TabsPresenter.Tab.HOME) {
            assertThat(model.content).isEqualTo(HomeModel)
          } else if (tab == TabsPresenter.Tab.DOWNLOADS) {
            assertThat(model.content).isEqualTo(DownloadsModel)
          }
        }
    }
  }

  @Test
  fun `library content keeps its filter while another tab is selected`() = runTest {
    val library = FakeLibraryPresenter()
    val presenter: TabsPresenter =
      TabsPresenterImpl(FakeHomePresenter(), library) { FakeDownloadsPresenter() }

    presenter.test(this) {
      var model = awaitItem()
      model.onSelectTab(TabsPresenter.Tab.LIBRARY)
      model = awaitItem()
      assertThat(model.content).isEqualTo(FakeLibraryPresenter.Model("Episodes"))

      library.selectFilter("Shows")
      model = awaitItem()
      assertThat(model.content).isEqualTo(FakeLibraryPresenter.Model("Shows"))

      listOf(TabsPresenter.Tab.HOME, TabsPresenter.Tab.DOWNLOADS).forEach { tab ->
        model.onSelectTab(tab)
        model = awaitItem()
        model.onSelectTab(TabsPresenter.Tab.LIBRARY)
        model = awaitItem()
        assertThat(model.content).isEqualTo(FakeLibraryPresenter.Model("Shows"))
      }
    }
  }

  private class FakeDownloadsPresenter : DownloadsPresenter<DownloadsModel> {
    @Composable override fun present(input: Unit): DownloadsModel = DownloadsModel
  }

  private data object DownloadsModel : BaseModel

  private data object HomeModel : BaseModel

  private class FakeHomePresenter : HomePresenter<HomeModel> {
    @Composable override fun present(input: Unit): HomeModel = HomeModel
  }

  private class FakeLibraryPresenter : LibraryPresenter<FakeLibraryPresenter.Model> {
    var selectFilter: (String) -> Unit = {}

    @Composable
    override fun present(input: Unit): Model {
      var filter by remember { mutableStateOf("Episodes") }
      selectFilter = { filter = it }
      return Model(filter)
    }

    data class Model(val filter: String) : BaseModel
  }
}
