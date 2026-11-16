package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.saveable.rememberReturningSaveableStateHolder
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveryPresenter
import software.ralf.storymile.library.LibraryPresenterImpl.Tab

class LibraryPresenterImplTest {
  @Test
  fun `tab survives leaving composition and creating a new presenter`() = runTest {
    val visible = MutableStateFlow(true)
    val presenter =
      ComposePresenter<Boolean, LibraryVisibilityModel> { showLibrary ->
        val stateHolder = rememberReturningSaveableStateHolder()
        stateHolder.SaveableStateProvider(if (showLibrary) "library" else "hidden") {
          LibraryVisibilityModel(
            if (showLibrary) {
              val library = remember { createPresenter() }
              library.present(Unit).root()
            } else {
              null
            },
          )
        }
      }

    presenter.test(this, visible) {
      assertThat(awaitItem().library).isNotNull().given {
        it.onSelectTab(Tab.HISTORY)
      }
      assertThat(awaitItem().library).isNotNull().given {
        assertThat(it.selectedTab).isEqualTo(Tab.HISTORY)
      }

      visible.value = false
      assertThat(awaitItem().library).isNull()

      visible.value = true
      assertThat(awaitItem().library).isNotNull().given {
        assertThat(it.selectedTab).isEqualTo(Tab.HISTORY)
      }
    }
  }

  @Test
  fun `tabs select queue shows saved or history without leaving the library`() = runTest {
    val presenter = createPresenter()

    presenter.test(this) {
      var model = awaitItem().root()
      assertThat(model.selectedTab).isEqualTo(Tab.QUEUE)

      model.onSelectTab(Tab.SHOWS)
      model = awaitItem().root()
      assertThat(model.selectedTab).isEqualTo(Tab.SHOWS)

      model.onSelectTab(Tab.SAVED)
      model = awaitItem().root()
      assertThat(model.selectedTab).isEqualTo(Tab.SAVED)

      model.onSelectTab(Tab.HISTORY)
      model = awaitItem().root()
      assertThat(model.selectedTab).isEqualTo(Tab.HISTORY)

      model.onSelectTab(Tab.QUEUE)
      model = awaitItem().root()
      assertThat(model.selectedTab).isEqualTo(Tab.QUEUE)
    }
  }

  @Test
  fun `every empty tab opens discovery and back preserves its selection`() = runTest {
    val presenter = createPresenter()

    presenter.test(this) {
      var library = awaitItem().root()
      Tab.entries.forEach { tab ->
        if (library.selectedTab != tab) {
          library.onSelectTab(tab)
          library = awaitItem().root()
        }
        library.onDiscoverPodcasts()

        val discovery = awaitItem().content
        assertThat(discovery.backstack.size).isEqualTo(2)
        assertThat(discovery.backstack.last()).isEqualTo(DiscoveryModel(focusSearch = false))
        discovery.onBack()

        val returned = awaitItem()
        library = returned.root()
        assertThat(library.selectedTab).isEqualTo(tab)
        returned.content.onBack()
        expectNoEvents()
      }
    }
  }

  private fun createPresenter(): LibraryPresenterImpl =
    LibraryPresenterImpl(
      discoveryPresenterFactory = FakeDiscoveryFactory(),
      queuePresenter = EmptyLibraryQueuePresenter,
      showsPresenter = EmptyLibraryShowsPresenter,
      savedPresenter = EmptyLibrarySavedPresenter,
    )

  private fun LibraryPresenterImpl.BackstackModel.root(): LibraryPresenterImpl.Model =
    content.backstack.single() as LibraryPresenterImpl.Model

  private class FakeDiscoveryFactory : DiscoveryPresenter.Factory {
    override fun create(focusSearch: Boolean): DiscoveryPresenter<*> =
      object : DiscoveryPresenter<DiscoveryModel> {
        @Composable override fun present(input: Unit): DiscoveryModel = DiscoveryModel(focusSearch)
      }
  }

  private data class DiscoveryModel(val focusSearch: Boolean) : BaseModel

  private data class LibraryVisibilityModel(val library: LibraryPresenterImpl.Model?) : BaseModel
}
