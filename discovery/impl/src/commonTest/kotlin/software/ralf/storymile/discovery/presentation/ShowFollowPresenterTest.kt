package software.ralf.storymile.discovery.presentation

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State as CatalogState
import software.ralf.storymile.episodedetails.testing.FakeEpisodeDetailsPresenterFactory
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository.State as FeedState

class ShowFollowPresenterTest {
  @Test
  fun `following and unfollowing update the stored shows`() = runTest {
    val savedShows = TestSavedShowRepository()
    presenter(savedShows).test(this) {
      awaitModel { it.followEnabled }.onToggleFollow()
      val followed = awaitModel { it.followed && it.followEnabled }
      assertThat(savedShows.shows.value.orEmpty()).containsExactly(feed("one"))

      followed.onToggleFollow()
      awaitModel { !it.followed && it.followEnabled }
      assertThat(savedShows.shows.value.orEmpty()).isEmpty()
    }
  }

  @Test
  fun `saved shows can be unfollowed after reopening when the feed is unavailable`() = runTest {
    val savedShows = TestSavedShowRepository()
    savedShows.save(feed("one"))
    val feeds = TestPodcastFeeds().apply { update("one", FeedState.Error) }
    presenter(savedShows = savedShows, feeds = feeds).test(this) {
      val followed = awaitModel { it.followed }
      assertThat(followed.followEnabled).isTrue()
      followed.onToggleFollow()
      awaitModel { !it.followed }
      assertThat(savedShows.shows.value.orEmpty()).isEmpty()
    }
    presenter(savedShows).test(this) {
      assertThat(awaitModel { it.followEnabled }.followed).isFalse()
    }
  }

  @Test
  fun `switching shows uses their own follow state`() = runTest {
    val savedShows = TestSavedShowRepository()
    presenter(savedShows).test(this) {
      awaitModel { it.followEnabled }.onToggleFollow()
      val first = awaitModel { it.followed && it.followEnabled }
      first.onPodcastSelected(podcast("two"))
      val second = awaitModel { it.podcast.id == "two" && it.followEnabled }

      assertThat(second.followed).isFalse()
      second.onPodcastSelected(podcast("one"))
      assertThat(awaitModel { it.podcast.id == "one" }.followed).isTrue()
    }
  }

  @Test
  fun `lookup supplies the feed URL and existing saves are observed`() = runTest {
    val savedShows = TestSavedShowRepository()
    val catalog = TestPodcastCatalog()
    presenter(
        savedShows = savedShows,
        catalog = catalog,
        initialPodcast = podcast("one").copy(feedUrl = null),
      )
      .test(this) {
        assertThat(awaitItem().followEnabled).isFalse()
        catalog.updateLookup(
          id = "one",
          state = CatalogState.Success(listOf(podcast("one").copy(feedUrl = " one "))),
        )
        awaitModel { it.followEnabled }.onToggleFollow()
        awaitModel { it.followed && it.followEnabled }
        assertThat(savedShows.shows.value.orEmpty()).containsExactly(feed("one"))
        savedShows.remove("one")
        assertThat(awaitModel { !it.followed && it.followEnabled }.followEnabled).isTrue()
      }
  }

  @Test
  fun `follow is disabled until saved shows load`() = runTest {
    val savedShows = TestSavedShowRepository().apply { publish(null) }
    presenter(savedShows).test(this) {
      val loading = awaitItem()
      assertThat(loading.followEnabled).isFalse()
      assertThat(loading.followed).isFalse()
      savedShows.publish(listOf(feed("one")))
      assertThat(awaitModel { it.followEnabled }.followed).isTrue()
    }
  }

  private fun presenter(
    savedShows: TestSavedShowRepository,
    feeds: TestPodcastFeeds =
      TestPodcastFeeds().apply {
        update("one", FeedState.Success(this@ShowFollowPresenterTest.feed("one")))
        update("two", FeedState.Success(this@ShowFollowPresenterTest.feed("two")))
      },
    catalog: TestPodcastCatalog = TestPodcastCatalog(),
    initialPodcast: DiscoveredPodcast = podcast("one"),
  ) =
    ShowContentPresenter(
        catalog = catalog,
        feeds = feeds,
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        savedShows = savedShows,
        initialPodcast = initialPodcast,
        podcasts = listOf(podcast("one"), podcast("two")),
        source = ShowPresenter.Source.Browse,
        onBack = {},
      )
      .withPresenterBackstackScope(FakePresenterBackstackScope())

  private suspend fun ReceiveTurbine<ShowContentPresenter.Model>.awaitModel(
    predicate: (ShowContentPresenter.Model) -> Boolean,
  ): ShowContentPresenter.Model {
    while (true) {
      val model = awaitItem()
      if (predicate(model)) {
        return model
      }
    }
  }

  private fun podcast(id: String) =
    DiscoveredPodcast(
      id = id,
      title = "Show $id",
      author = "Author",
      feedUrl = id,
      artworkUrl = null,
      catalogUrl = null,
    )

  private fun feed(id: String) =
    PodcastFeed(
      feedUrl = id,
      title = "Show $id",
      author = "Author",
      description = "Show description",
      artworkUrl = null,
      link = "https://shows.test/$id",
      episodes =
        listOf(
          PodcastEpisode(
            id = "episode",
            title = "An episode",
            description = "Episode description",
            author = null,
            artworkUrl = null,
            publishedAt = null,
            duration = null,
            enclosure =
              PodcastEnclosure(url = "https://audio.test/$id.mp3", type = null, length = null),
          ),
        ),
    )
}
