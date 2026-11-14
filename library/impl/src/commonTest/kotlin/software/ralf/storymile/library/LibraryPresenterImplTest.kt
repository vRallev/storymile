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
import software.ralf.storymile.library.LibraryPresenterImpl.FollowedShowsState.Ready
import software.ralf.storymile.library.LibraryPresenterImpl.NavigationModel
import software.ralf.storymile.library.LibraryPresenterImpl.Tab
import software.ralf.storymile.podcast.PodcastFeed

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
              library.present(Unit).content()
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
      var model = awaitItem().content()
      assertThat(model.selectedTab).isEqualTo(Tab.QUEUE)

      model.onSelectTab(Tab.SHOWS)
      model = awaitItem().content()
      assertThat(model.selectedTab).isEqualTo(Tab.SHOWS)

      model.onSelectTab(Tab.SAVED)
      model = awaitItem().content()
      assertThat(model.selectedTab).isEqualTo(Tab.SAVED)

      model.onSelectTab(Tab.HISTORY)
      model = awaitItem().content()
      assertThat(model.selectedTab).isEqualTo(Tab.HISTORY)

      model.onSelectTab(Tab.QUEUE)
      model = awaitItem().content()
      assertThat(model.selectedTab).isEqualTo(Tab.QUEUE)
    }
  }

  @Test
  fun `every empty tab opens discovery and back preserves its selection`() = runTest {
    val presenter = createPresenter()

    presenter.test(this) {
      var library = awaitItem().content()
      Tab.entries.forEach { tab ->
        if (library.selectedTab != tab) {
          library.onSelectTab(tab)
          library = awaitItem().content()
        }
        library.onDiscoverPodcasts()

        val discovery = awaitItem()
        assertThat(discovery.backstack.size).isEqualTo(2)
        assertThat(discovery.backstack.last()).isEqualTo(DiscoveryModel(focusSearch = false))
        discovery.onBack()

        val returned = awaitItem()
        library = returned.content()
        assertThat(library.selectedTab).isEqualTo(tab)
        returned.onBack()
        expectNoEvents()
      }
    }
  }

  @Test
  fun `follow and unfollow update the list while show selection replaces the detail`() = runTest {
    val database = TestLibraryDatabase()
    val first = feed("first")
    val second = feed("second")
    createPresenter(database).test(this) {
      val initial = awaitItem().content()
      val loaded = if (initial.shows is Ready) initial else awaitItem().content()
      assertThat(loaded.shows).isEqualTo(Ready(emptyList()))
      loaded.onSelectTab(Tab.SHOWS)
      assertThat(awaitItem().content().selectedTab).isEqualTo(Tab.SHOWS)
      database.insert(first)
      assertThat(awaitItem().content().shows).isEqualTo(Ready(listOf(first)))
      database.insert(second)
      val listed = awaitItem().content()
      assertThat(listed.shows).isEqualTo(Ready(listOf(first, second)))
      listed.onShowSelected(first)
      val opened = awaitItem()
      assertThat(opened.backstack.last()).isEqualTo(TestShowFactory.Model(first))
      (opened.backstack.first() as LibraryPresenterImpl.Model).onShowSelected(second)

      val replaced = awaitItem()
      assertThat(replaced.backstack.size).isEqualTo(2)
      assertThat(replaced.backstack.last()).isEqualTo(TestShowFactory.Model(second))
      database.delete(first.feedUrl)
      val updated = awaitItem()
      updated.onBack()
      val returned = awaitItem().content()
      assertThat(returned.shows).isEqualTo(Ready(listOf(second)))
      assertThat(returned.selectedTab).isEqualTo(Tab.SHOWS)
    }
  }

  private fun feed(name: String) =
    PodcastFeed(
      feedUrl = "https://example.test/$name.xml",
      title = name,
      description = null,
      author = null,
      artworkUrl = null,
      link = null,
      episodes = emptyList(),
    )

  private fun createPresenter(
    database: TestLibraryDatabase = TestLibraryDatabase(),
  ): LibraryPresenterImpl =
    LibraryPresenterImpl(
      discoveryPresenterFactory = FakeDiscoveryFactory(),
      showFactory = TestShowFactory,
      database = database,
    )

  private fun NavigationModel.content(): LibraryPresenterImpl.Model =
    backstack.single() as LibraryPresenterImpl.Model

  private class FakeDiscoveryFactory : DiscoveryPresenter.Factory {
    override fun create(focusSearch: Boolean): DiscoveryPresenter<*> =
      object : DiscoveryPresenter<DiscoveryModel> {
        @Composable override fun present(input: Unit): DiscoveryModel = DiscoveryModel(focusSearch)
      }
  }

  private data class DiscoveryModel(val focusSearch: Boolean) : BaseModel

  private data class LibraryVisibilityModel(val library: LibraryPresenterImpl.Model?) : BaseModel
}
