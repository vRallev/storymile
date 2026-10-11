@file:OptIn(ExperimentalAppPlatform::class)

package software.ralf.storymile.tabs

import androidx.compose.runtime.Composable
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.zacsweers.metro.Provider
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.ExperimentalAppPlatform
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.downloads.DownloadsPresenter
import software.ralf.storymile.home.HomePresenter

class TabsPresenterImplTest {
  @Test
  fun `tabs start at home and selection updates the destination`() = runTest {
    val home =
      object : HomePresenter<HomeModel> {
        @Composable override fun present(input: Unit): HomeModel = HomeModel
      }
    val presenter: TabsPresenter = TabsPresenterImpl(home, Provider { FakeDownloadsPresenter() })

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

  private class FakeDownloadsPresenter : DownloadsPresenter<DownloadsModel> {
    @Composable override fun present(input: Unit): DownloadsModel = DownloadsModel
  }

  private data object DownloadsModel : BaseModel

  private data object HomeModel : BaseModel
}
