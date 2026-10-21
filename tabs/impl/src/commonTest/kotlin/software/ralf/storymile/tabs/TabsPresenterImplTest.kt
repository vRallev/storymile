package software.ralf.storymile.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import software.ralf.storymile.home.testing.FakeHomePresenter
import software.ralf.storymile.library.LibraryPresenter

class TabsPresenterImplTest {
  @Test
  fun `tabs start at home and selection updates the destination`() = runTest {
    val presenter: TabsPresenter =
      TabsPresenterImpl({ FakeHomePresenter { HomeModel } }, { FakeLibraryPresenter() }) {
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
    val home = FakeHomePresenter { HomeModel }
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
      TabsPresenterImpl({ FakeHomePresenter { HomeModel } }, { library }) {
        FakeDownloadsPresenter()
      }

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

  @Test
  fun `reselecting home calls its reset without recreating home state`() = runTest {
    val home = HomeNavigation()
    var creationCount = 0
    val presenter: TabsPresenter =
      TabsPresenterImpl(
        homePresenter = {
          creationCount++
          home.presenter
        },
        libraryPresenter = { FakeLibraryPresenter() },
        downloadsPresenter = { FakeDownloadsPresenter() },
      )

    presenter.test(this) {
      var tabs = awaitItem()
      repeat(2) { index ->
        (tabs.content as HomeNavigation.Model).onNavigate()
        tabs = awaitItem()
        assertThat((tabs.content as HomeNavigation.Model).destination).isEqualTo("Discovery")

        tabs.onSelectTab(TabsPresenter.Tab.HOME)
        tabs = awaitItem()
        assertThat(tabs.selectedTab).isEqualTo(TabsPresenter.Tab.HOME)
        val homeModel = tabs.content as HomeNavigation.Model
        assertThat(homeModel.destination).isEqualTo("Welcome")
        assertThat(homeModel.navigationCount).isEqualTo(index + 1)
      }
      assertThat(creationCount).isEqualTo(1)
      assertThat(home.homeSelections).isEqualTo(2)
      tabs.onSelectTab(TabsPresenter.Tab.HOME)
      tabs.onSelectTab(TabsPresenter.Tab.HOME)
      expectNoEvents()
      assertThat(home.homeSelections).isEqualTo(4)
      assertThat(creationCount).isEqualTo(1)
    }
  }

  @Test
  fun `returning home starts at welcome and preserves the library filter`() = runTest {
    val library = FakeLibraryPresenter()
    val presenter: TabsPresenter =
      TabsPresenterImpl({ HomeNavigation().presenter }, { library }) { FakeDownloadsPresenter() }

    presenter.test(this) {
      var tabs = awaitItem()
      (tabs.content as HomeNavigation.Model).onNavigate()
      tabs = awaitItem()
      tabs.onSelectTab(TabsPresenter.Tab.LIBRARY)
      awaitItem()
      library.selectFilter("Shows")
      tabs = awaitItem()

      tabs.onSelectTab(TabsPresenter.Tab.HOME)
      tabs = awaitItem()
      assertThat((tabs.content as HomeNavigation.Model).destination).isEqualTo("Welcome")

      tabs.onSelectTab(TabsPresenter.Tab.LIBRARY)
      tabs = awaitItem()
      assertThat(tabs.content).isEqualTo(FakeLibraryPresenter.Model("Shows"))
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

  private data object HomeModel : HomePresenter.Model {
    override val onHomeSelected: () -> Unit = {}
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

  private class HomeNavigation {
    var homeSelections = 0
      private set

    val presenter = FakeHomePresenter {
      var destination by remember { mutableStateOf("Welcome") }
      var navigationCount by remember { mutableIntStateOf(0) }
      Model(
        destination = destination,
        navigationCount = navigationCount,
        onNavigate = {
          destination = "Discovery"
          navigationCount++
        },
        onHomeSelected = {
          homeSelections++
          destination = "Welcome"
        },
      )
    }

    data class Model(
      val destination: String,
      val navigationCount: Int,
      val onNavigate: () -> Unit,
      override val onHomeSelected: () -> Unit,
    ) : HomePresenter.Model
  }
}
