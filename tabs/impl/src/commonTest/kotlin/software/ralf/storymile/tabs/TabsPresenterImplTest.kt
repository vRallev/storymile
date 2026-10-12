package software.ralf.storymile.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.downloads.DownloadsPresenter
import software.ralf.storymile.home.HomePresenter
import software.ralf.storymile.library.LibraryPresenter

class TabsPresenterImplTest {
  @Test
  fun `tabs start at home and selection updates the destination`() = runTest {
    val presenter: TabsPresenter =
      TabsPresenterImpl({ FakeHomePresenter() }, { FakeLibraryPresenter() }) {
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
  fun `only selected screens create and run presenters`() = runTest {
    val home = FakeHomePresenter()
    val library = FakeLibraryPresenter()
    val downloads = FakeDownloadsPresenter()
    val created = mutableSetOf<TabsPresenter.Tab>()
    val presenter: TabsPresenter =
      TabsPresenterImpl(
        homePresenter = {
          created += TabsPresenter.Tab.HOME
          home
        },
        libraryPresenter = {
          created += TabsPresenter.Tab.LIBRARY
          library
        },
        downloadsPresenter = {
          created += TabsPresenter.Tab.DOWNLOADS
          downloads
        },
      )
    assertThat(created).isEqualTo(emptySet())

    presenter.test(this) {
      var model = awaitItem()
      assertThat(created).isEqualTo(setOf(TabsPresenter.Tab.HOME))
      assertThat(home.active).isTrue()
      assertThat(library.active).isFalse()
      assertThat(downloads.active).isFalse()

      model.onSelectTab(TabsPresenter.Tab.DOWNLOADS)
      model = awaitItem()
      assertThat(created).isEqualTo(setOf(TabsPresenter.Tab.HOME, TabsPresenter.Tab.DOWNLOADS))
      assertThat(home.active).isFalse()
      assertThat(library.active).isFalse()
      assertThat(downloads.active).isTrue()

      model.onSelectTab(TabsPresenter.Tab.LIBRARY)
      model = awaitItem()
      assertThat(created).isEqualTo(TabsPresenter.Tab.entries.toSet())
      assertThat(home.active).isFalse()
      assertThat(library.active).isTrue()
      assertThat(downloads.active).isFalse()

      model.onSelectTab(TabsPresenter.Tab.HOME)
      awaitItem()
      assertThat(home.active).isTrue()
      assertThat(library.active).isFalse()
      assertThat(downloads.active).isFalse()
    }
  }

  @Test
  fun `library content keeps its filter while another tab is selected`() = runTest {
    val library = FakeLibraryPresenter()
    val presenter: TabsPresenter =
      TabsPresenterImpl({ FakeHomePresenter() }, { library }) { FakeDownloadsPresenter() }

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
    var active = false
      private set

    @Composable
    override fun present(input: Unit): DownloadsModel {
      DisposableEffect(Unit) {
        active = true
        onDispose { active = false }
      }
      return DownloadsModel
    }
  }

  private data object DownloadsModel : BaseModel

  private data object HomeModel : BaseModel

  private class FakeHomePresenter : HomePresenter<HomeModel> {
    var active = false
      private set

    @Composable
    override fun present(input: Unit): HomeModel {
      DisposableEffect(Unit) {
        active = true
        onDispose { active = false }
      }
      return HomeModel
    }
  }

  private class FakeLibraryPresenter : LibraryPresenter<FakeLibraryPresenter.Model> {
    var selectFilter: (String) -> Unit = {}
    var active = false
      private set

    @Composable
    override fun present(input: Unit): Model {
      var filter by rememberSaveable { mutableStateOf("Episodes") }
      DisposableEffect(Unit) {
        active = true
        onDispose { active = false }
      }
      selectFilter = { filter = it }
      return Model(filter)
    }

    data class Model(val filter: String) : BaseModel
  }
}
