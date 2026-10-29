package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.saveable.rememberReturningSaveableStateHolder
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveryPresenter
import software.ralf.storymile.episodedetails.testing.FakeEpisodeDetailsPresenterFactory
import software.ralf.storymile.library.LibraryPresenterImpl.Tab
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.presenternavigation.DefaultBackstackModel

class LibraryPresenterImplTest {
  @Test
  fun `selected show survives leaving Library and recreating its backstack`() = runTest {
    val database = TestPodcastDatabase()
    listOf("one", "two").forEach { id ->
      database.insert(
        PodcastFeed(
          feedUrl = id,
          title = id,
          description = null,
          author = null,
          artworkUrl = null,
          link = null,
          episodes = emptyList(),
        ),
      )
    }
    val visible = MutableStateFlow(true)
    val presenter =
      ComposePresenter<Boolean, LibraryVisibilityModel> { showLibrary ->
        val stateHolder = rememberReturningSaveableStateHolder()
        stateHolder.SaveableStateProvider(if (showLibrary) "library" else "hidden") {
          LibraryVisibilityModel(
            if (showLibrary) {
              val library = remember {
                LibraryPresenterImpl(
                  discoveryPresenterFactory = FakeDiscoveryFactory(),
                  showsPresenter =
                    LibraryShowsPresenter(
                      database = database,
                      episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
                    ),
                )
              }
              library.present(Unit).content()
            } else {
              null
            },
          )
        }
      }
    presenter.test(this, visible) {
      runCurrent()
      val shows = requireNotNull(expectMostRecentItem().library?.shows)
      shows.onSelectShow(shows.shows.last())
      runCurrent()
      assertThat(expectMostRecentItem().library?.shows?.details?.podcast?.id).isEqualTo("two")
      visible.value = false
      assertThat(awaitItem().library).isNull()
      visible.value = true
      runCurrent()
      val restored = requireNotNull(expectMostRecentItem().library?.shows)
      assertThat(restored.details?.podcast?.id).isEqualTo("two")
      assertThat(restored.detailOpen).isTrue()
      cancelAndIgnoreRemainingEvents()
    }
  }

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

  private fun createPresenter(): LibraryPresenterImpl =
    LibraryPresenterImpl(
      discoveryPresenterFactory = FakeDiscoveryFactory(),
      showsPresenter = testShowsPresenter(),
    )

  private fun DefaultBackstackModel.content(): LibraryPresenterImpl.Model =
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
