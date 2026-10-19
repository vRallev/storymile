package software.ralf.storymile.discovery.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State

@OptIn(ExperimentalCoroutinesApi::class)
class ShowPresenterTest {
  @Test
  fun `description preloads when the show opens and expands without another request`() = runTest {
    val catalog = TestPodcastCatalog()
    presenter(catalog).withPresenterBackstackScope(FakePresenterBackstackScope()).test(this) {
      val initial = awaitItem()
      assertThat(initial.descriptionExpanded).isFalse()
      runCurrent()
      assertThat(catalog.lookedUp).containsExactly("one")

      val podcast = podcast("one").copy(description = "Stories & science.")
      catalog.updateLookup(id = podcast.id, state = State.Success(listOf(podcast)))
      val loaded = awaitItem()
      assertThat(loaded.podcast).isEqualTo(podcast)
      assertThat(loaded.descriptionExpanded).isFalse()
      assertThat(catalog.lookedUp).containsExactly("one")
      loaded.onToggleDescription()
      val expanded = awaitItem()
      assertThat(expanded.descriptionExpanded).isTrue()
      assertThat(expanded.podcast.description).isEqualTo("Stories & science.")
      expanded.onToggleDescription()
      val collapsed = awaitItem()
      assertThat(collapsed.descriptionExpanded).isFalse()
      assertThat(collapsed.podcast.description).isEqualTo("Stories & science.")
      collapsed.onToggleDescription()
      assertThat(awaitItem().podcast.description).isEqualTo("Stories & science.")
      assertThat(catalog.lookedUp).containsExactly("one")
    }
  }

  @Test
  fun `initial model uses the repository metadata and keeps it on expansion`() = runTest {
    listOf("Cached description", null).forEach { description ->
      val catalog = TestPodcastCatalog()
      val cached = podcast("one").copy(title = "Cached title", description = description)
      catalog.updateLookup(id = cached.id, state = State.Success(listOf(cached)))
      presenter(catalog).withPresenterBackstackScope(FakePresenterBackstackScope()).test(this) {
        val initial = awaitItem()
        assertThat(initial.podcast).isEqualTo(cached)
        initial.onToggleDescription()
        val expanded = awaitItem()
        assertThat(expanded.descriptionExpanded).isTrue()
        assertThat(expanded.podcast).isEqualTo(cached)
      }
    }
  }

  @Test
  fun `selecting another podcast clears its description and preloads while collapsed`() = runTest {
    val catalog = TestPodcastCatalog()
    presenter(catalog).withPresenterBackstackScope(FakePresenterBackstackScope()).test(this) {
      awaitItem().onToggleDescription()
      awaitItem()
      catalog.updateLookup(
        id = "one",
        state = State.Success(listOf(podcast("one").copy(description = "First description"))),
      )
      awaitItem().onPodcastSelected(podcast("two"))
      val switched = awaitItem()
      assertThat(switched.descriptionExpanded).isFalse()
      assertThat(switched.podcast.description).isNull()
      runCurrent()
      assertThat(catalog.lookedUp).containsExactly("one", "two")
      catalog.updateLookup(
        id = "one",
        state = State.Success(listOf(podcast("one").copy(description = "Old update"))),
      )
      runCurrent()
      expectNoEvents()
      catalog.updateLookup(
        id = "two",
        state = State.Success(listOf(podcast("two").copy(description = "Second description"))),
      )
      val preloaded = awaitItem()
      assertThat(preloaded.descriptionExpanded).isFalse()
      assertThat(preloaded.podcast.description).isEqualTo("Second description")
      preloaded.onToggleDescription()
      assertThat(awaitItem().descriptionExpanded).isTrue()
      assertThat(catalog.lookedUp).containsExactly("one", "two")
    }
  }

  @Test
  fun `selected metadata remains visible until lookup provides a matching podcast`() = runTest {
    val catalog = TestPodcastCatalog()
    val selected = podcast("one").copy(description = "Chart summary")
    catalog.updateLookup(id = selected.id, state = State.Loading)
    presenter(catalog = catalog, initialPodcast = selected)
      .withPresenterBackstackScope(FakePresenterBackstackScope())
      .test(this) {
        val initial = awaitItem()
        assertThat(initial.podcast).isEqualTo(selected)
        initial.onToggleDescription()
        val expanded = awaitItem()
        assertThat(expanded.podcast.description).isEqualTo("Chart summary")
        catalog.updateLookup(id = selected.id, state = State.Error)
        assertThat(awaitItem().podcast).isEqualTo(selected)
        catalog.updateLookup(
          id = selected.id,
          state = State.Success(listOf(podcast("two").copy(description = "Another show"))),
        )
        assertThat(awaitItem().podcast).isEqualTo(selected)
        val loaded = selected.copy(description = "Updated summary")
        catalog.updateLookup(id = selected.id, state = State.Success(listOf(loaded)))
        assertThat(awaitItem().podcast).isEqualTo(loaded)
      }
  }

  private fun presenter(
    catalog: TestPodcastCatalog,
    initialPodcast: DiscoveredPodcast = podcast("one"),
  ) =
    ShowPresenter(
      catalog = catalog,
      feeds = TestPodcastFeeds(),
      episodeDetailsFactory = TestEpisodeDetailsFactory(),
      initialPodcast = initialPodcast,
      podcasts = listOf(podcast("one"), podcast("two")),
      source = ShowPresenter.Source.Browse,
    )

  private fun podcast(id: String) =
    DiscoveredPodcast(
      id = id,
      title = "Show $id",
      author = null,
      feedUrl = id,
      artworkUrl = null,
      catalogUrl = null,
    )
}
