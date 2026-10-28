package software.ralf.storymile.discovery.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State as CatalogState
import software.ralf.storymile.episodedetails.testing.FakeEpisodeDetailsPresenterFactory
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository.State
import software.ralf.storymile.shows.ShowEpisodes

class EpisodesPresenterTest {
  @Test
  fun `selecting an episode pushes reusable details with a plain text description`() = runTest {
    val feeds = TestPodcastFeeds()
    val feed = feed("one", "first")
    val episode =
      feed.episodes
        .single()
        .copy(description = "<p title=\"1 > 0\">Caf&eacute; &amp; stories.<p>Next paragraph")
    feeds.update("one", State.Success(feed.copy(episodes = listOf(episode))))
    val factory = FakeEpisodeDetailsPresenterFactory()
    val presenter = EpisodesPresenter(TestPodcastCatalog(), feeds, factory)
    val backstack = FakePresenterBackstackScope()
    val initialBackstack = backstack.backstack.toList()
    presenter.withPresenterBackstackScope(backstack).test(this, listOf(podcast("one"))) {
      val item = awaitItem().episodes.single()
      assertThat(item.details.description).isEqualTo("Café & stories.\n\nNext paragraph")
      item.onClick()
      assertThat(backstack.backstack).isEqualTo(initialBackstack + factory.presenter)
      assertThat(factory.details).isEqualTo(item.details)
    }
  }

  @Test
  fun `shows episodes in feed order and updates the snapshot`() = runTest {
    val feeds = TestPodcastFeeds()
    val presenter: ComposePresenter<List<DiscoveredPodcast>, ShowEpisodes> =
      EpisodesPresenter(TestPodcastCatalog(), feeds, FakeEpisodeDetailsPresenterFactory())
    presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(
      testScope = this,
      input = listOf(podcast("one")),
    ) {
      assertThat(awaitItem().loading).isTrue()
      feeds.update("one", State.Success(feed("one", "second", "first")))
      val ready = awaitItem()
      assertThat(ready.loading).isFalse()
      assertThat(ready.episodes.map { it.details.episode.title }).containsExactly("second", "first")
      assertThat(ready.episodes.map { it.details.podcastTitle })
        .containsExactly("Show one", "Show one")
      feeds.update("one", State.Success(feed("one", "new")))
      assertThat(awaitItem().episodes.map { it.details.episode.title }).containsExactly("new")
    }
  }

  @Test
  fun `discovery keeps successes while another feed loads or fails and retries only failures`() =
    runTest {
      val feeds = TestPodcastFeeds()
      val presenter: ComposePresenter<List<DiscoveredPodcast>, ShowEpisodes> =
        EpisodesPresenter(
          catalog = TestPodcastCatalog(),
          repository = feeds,
          episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
          episodesPerPodcast = 1,
        )
      presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(
        testScope = this,
        input = listOf(podcast("one"), podcast("two")),
      ) {
        awaitItem()
        feeds.update("one", State.Success(feed("one", "first", "older")))
        val partial = awaitItem()
        assertThat(partial.loading).isTrue()
        assertThat(partial.episodes.map { it.details.episode.title }).containsExactly("first")
        feeds.update("two", State.Error)
        val failed = awaitItem()
        assertThat(failed.failed).isTrue()
        assertThat(failed.loading).isFalse()
        assertThat(failed.episodes.map { it.details.episode.title }).containsExactly("first")
        failed.onRetry()
        assertThat(feeds.refreshed).containsExactly("two")
        assertThat(awaitItem().loading).isTrue()
        feeds.update("two", State.Success(feed("two", "latest", "older")))
        val ready = awaitItem()
        assertThat(ready.failed).isFalse()
        assertThat(ready.episodes.map { it.details.episode.title })
          .containsExactly("first", "latest")
      }
    }

  @Test
  fun `switching podcasts drops previous episodes and ignores old feed updates`() = runTest {
    val feeds = TestPodcastFeeds()
    feeds.update("one", State.Success(feed("one", "old")))
    val input = MutableStateFlow(listOf(podcast("one")))
    val presenter: ComposePresenter<List<DiscoveredPodcast>, ShowEpisodes> =
      EpisodesPresenter(TestPodcastCatalog(), feeds, FakeEpisodeDetailsPresenterFactory())
    presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(this, input) {
      assertThat(awaitItem().episodes.map { it.details.episode.title }).containsExactly("old")
      input.value = listOf(podcast("two"))
      val changed = awaitItem()
      assertThat(changed.loading).isTrue()
      assertThat(changed.episodes).isEmpty()
      feeds.update("one", State.Success(feed("one", "late")))
      expectNoEvents()
      feeds.update("two", State.Success(feed("two", "current")))
      assertThat(awaitItem().episodes.map { it.details.episode.title }).containsExactly("current")
    }
  }

  @Test
  fun `missing feeds and feeds without episodes produce an empty state`() = runTest {
    val feeds = TestPodcastFeeds()
    val presenter: ComposePresenter<List<DiscoveredPodcast>, ShowEpisodes> =
      EpisodesPresenter(TestPodcastCatalog(), feeds, FakeEpisodeDetailsPresenterFactory())
    presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(
      testScope = this,
      input = listOf(podcast(null), podcast(" ")),
    ) {
      val empty = awaitItem()
      assertThat(empty.episodes).isEmpty()
      assertThat(empty.loading).isFalse()
      assertThat(empty.failed).isFalse()
      assertThat(feeds.requested).isEmpty()
    }
    feeds.update("one", State.Success(feed("one")))
    presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(
      testScope = this,
      input = listOf(podcast("one"), podcast(" one ")),
    ) {
      val empty = awaitItem()
      assertThat(empty.episodes).isEmpty()
      assertThat(empty.loading).isFalse()
      assertThat(empty.failed).isFalse()
      assertThat(feeds.requested).containsExactly("one")
    }
  }

  @Test
  fun `chart entries resolve feed URLs before loading episodes and lookup failures can retry`() =
    runTest {
      val catalog = TestPodcastCatalog()
      val feeds = TestPodcastFeeds()
      catalog.updateLookup("one", CatalogState.Loading)
      val presenter: ComposePresenter<List<DiscoveredPodcast>, ShowEpisodes> =
        EpisodesPresenter(catalog, feeds, FakeEpisodeDetailsPresenterFactory())
      presenter.withPresenterBackstackScope(FakePresenterBackstackScope()).test(
        testScope = this,
        input = listOf(podcast("one").copy(feedUrl = null)),
      ) {
        assertThat(awaitItem().loading).isTrue()
        assertThat(feeds.requested).isEmpty()
        catalog.updateLookup("one", CatalogState.Error)
        val failed = awaitItem()
        assertThat(failed.failed).isTrue()
        failed.onRetry()
        assertThat(catalog.refreshedLookups).containsExactly("one")
        assertThat(awaitItem().loading).isTrue()
        catalog.updateLookup("one", CatalogState.Success(listOf(podcast("one"))))
        // Both lookup and RSS loading render as loading; model emissions may be conflated.
        feeds.update("one", State.Success(feed("one", "resolved episode")))
        var ready = awaitItem()
        while (ready.loading) {
          ready = awaitItem()
        }
        assertThat(ready.episodes.map { it.details.episode.title })
          .containsExactly("resolved episode")
        assertThat(feeds.requested).containsExactly("one")
      }
    }

  private fun podcast(url: String?) =
    DiscoveredPodcast("$url", "Catalog title", null, url, null, null)

  private fun feed(url: String, vararg titles: String) =
    PodcastFeed(
      url,
      "Show $url",
      null,
      null,
      null,
      null,
      titles.map { title ->
        PodcastEpisode(
          title,
          title,
          null,
          null,
          null,
          null,
          null,
          PodcastEnclosure("https://audio.test/$title.mp3", "audio/mpeg", null),
        )
      },
    )
}
