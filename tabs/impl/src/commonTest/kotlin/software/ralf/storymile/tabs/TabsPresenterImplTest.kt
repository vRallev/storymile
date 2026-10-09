@file:OptIn(ExperimentalAppPlatform::class)

package software.ralf.storymile.tabs

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.ExperimentalAppPlatform
import software.ralf.app.platform.presenter.compose.test

class TabsPresenterImplTest {
  @Test
  fun `tabs start at home and selection updates the destination`() = runTest {
    val presenter: TabsPresenter = TabsPresenterImpl()

    presenter.test(this) {
      var model = awaitItem()
      assertThat(model.selectedTab).isEqualTo(TabsPresenter.Tab.HOME)

      listOf(TabsPresenter.Tab.DOWNLOADS, TabsPresenter.Tab.LIBRARY, TabsPresenter.Tab.HOME)
        .forEach { tab ->
          model.onSelectTab(tab)
          model = awaitItem()
          assertThat(model.selectedTab).isEqualTo(tab)
        }
    }
  }
}
