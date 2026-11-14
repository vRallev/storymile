package software.ralf.storymile.discovery.presentation

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.DiscoveryTopic
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.episodedetails.testing.FakeEpisodeDetailsPresenterFactory
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.podcastshows.ShowSource

class DiscoveryPresenterImplTest {
  @Test
  fun `browsing observes recommendations and retry requests a manual refresh`() = runTest {
    val repository = TestPodcastCatalog()
    repository.updatePopular(State.Loading)
    val presenter =
      DiscoveryPresenterImpl(
        repository = repository,
        feeds = TestPodcastFeeds(),
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        showPresenterFactory = TestShowPresenterFactory(),
      )

    presenter.testCatalog(this) {
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      repository.updatePopular(State.Error)
      val failed = awaitItem()
      assertThat(failed.state).isEqualTo(CatalogState.Error)
      failed.onRetry()
      assertThat(repository.refreshRequests).isEqualTo(1)
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      repository.updatePopular(State.Success(listOf(podcast("popular"))))
      assertThat(awaitItem().state).isEqualTo(CatalogState.Ready(listOf(podcast("popular"))))

      repository.updatePopular(State.Loading)
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      repository.updatePopular(State.Success(listOf(podcast("refreshed"))))
      assertThat(awaitItem().state).isEqualTo(CatalogState.Ready(listOf(podcast("refreshed"))))
      assertThat(repository.queries).isEmpty()
    }
  }

  @Test
  fun `typing replaces results after debounce and clearing returns to featured`() = runTest {
    val repository = TestPodcastCatalog()
    val presenter =
      DiscoveryPresenterImpl(
        repository = repository,
        feeds = TestPodcastFeeds(),
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        showPresenterFactory = TestShowPresenterFactory(),
      )

    presenter.testCatalog(this) {
      val initial = awaitItem()
      initial.query.replaceText(" sci ")
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      advanceTimeBy(200)
      initial.query.replaceText(" science ")
      runCurrent()
      advanceTimeBy(299)
      runCurrent()
      assertThat(repository.queries).isEmpty()
      advanceTimeBy(1)
      runCurrent()
      val results = expectMostRecentItem()
      assertThat(results.state).isInstanceOf<CatalogState.Ready>()
      assertThat(repository.queries).containsExactly("science")

      repository.updatePopular(State.Success(listOf(podcast("refreshed"))))
      runCurrent()
      expectNoEvents()
      results.query.clearText()
      assertThat(awaitItem().state).isEqualTo(CatalogState.Ready(listOf(podcast("refreshed"))))
      assertThat(repository.refreshRequests).isEqualTo(0)
    }
  }

  @Test
  fun `retrying a search does not refresh recommendations`() = runTest {
    val repository = TestPodcastCatalog()
    val presenter =
      DiscoveryPresenterImpl(
        repository = repository,
        feeds = TestPodcastFeeds(),
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        showPresenterFactory = TestShowPresenterFactory(),
      )

    presenter.testCatalog(this) {
      val initial = awaitItem()
      repository.response = HttpResponse.Error.Server(503)
      initial.query.replaceText("science")
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      val failed = awaitItem()
      assertThat(failed.state).isEqualTo(CatalogState.Error)

      repository.response = HttpResponse.Success(listOf(podcast("search")), 200)
      failed.onRetry()
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      assertThat(awaitItem().state).isEqualTo(CatalogState.Ready(listOf(podcast("search"))))
      assertThat(repository.queries).containsExactly("science", "science")
      assertThat(repository.refreshRequests).isEqualTo(0)
    }
  }

  @Test
  fun `superseded request cannot replace newer search results`() = runTest {
    val repository = TestPodcastCatalog()
    val oldResponse = CompletableDeferred<HttpResponse<List<DiscoveredPodcast>>>()
    repository.pending["old"] = oldResponse
    val presenter =
      DiscoveryPresenterImpl(
        repository = repository,
        feeds = TestPodcastFeeds(),
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        showPresenterFactory = TestShowPresenterFactory(),
      )

    presenter.testCatalog(this) {
      val initial = awaitItem()
      initial.query.replaceText("old")
      awaitItem()
      advanceTimeBy(300)
      runCurrent()
      assertThat(repository.queries).containsExactly("old")

      repository.response = HttpResponse.Success(listOf(podcast("new")), 200)
      initial.query.replaceText("new")
      runCurrent()
      advanceTimeBy(300)
      runCurrent()
      assertThat(expectMostRecentItem().state).isEqualTo(CatalogState.Ready(listOf(podcast("new"))))

      oldResponse.complete(HttpResponse.Success(listOf(podcast("old")), 200))
      runCurrent()
      expectNoEvents()
      assertThat(repository.queries).containsExactly("old", "new")
    }
  }

  @Test
  fun `topic filters search their keywords and empty responses stay empty`() = runTest {
    val repository = TestPodcastCatalog()
    repository.response = HttpResponse.Success(emptyList(), 200)
    val presenter =
      DiscoveryPresenterImpl(
        repository = repository,
        feeds = TestPodcastFeeds(),
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        showPresenterFactory = TestShowPresenterFactory(),
        topic = DiscoveryTopic.NATURE,
      )

    presenter.testCatalog(this) {
      awaitItem()
      val initial = awaitItem()
      assertThat(initial.state).isEqualTo(CatalogState.Ready(emptyList()))
      assertThat(repository.queries).containsExactly("Nature")
      initial.onFilterSelected(DiscoveryTopic.Filter.OCEANS)
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      val filtered = awaitItem()
      assertThat(filtered.selectedFilter).isEqualTo(DiscoveryTopic.Filter.OCEANS)
      assertThat(repository.queries).containsExactly("Nature", "Nature Oceans")

      filtered.onFilterSelected(DiscoveryTopic.Filter.ALL_NATURE)
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      assertThat(awaitItem().selectedFilter).isEqualTo(DiscoveryTopic.Filter.ALL_NATURE)
      assertThat(repository.queries).containsExactly("Nature", "Nature Oceans", "Nature")
      assertThat(repository.refreshRequests).isEqualTo(0)
    }
  }

  @Test
  fun `opening a show passes the selected podcast and ordered catalog`() = runTest {
    val shows = TestShowPresenterFactory()
    val repository = TestPodcastCatalog()
    val podcasts = listOf(podcast("first"), podcast("second"))
    repository.updatePopular(State.Success(podcasts))
    val presenter =
      DiscoveryPresenterImpl(
        repository = repository,
        feeds = TestPodcastFeeds(),
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        showPresenterFactory = shows,
      )
    val backstack = FakePresenterBackstackScope(presenter)

    presenter.testCatalog(this, backstack) {
      val model = awaitItem()
      model.onPodcastSelected(podcasts[1])
      assertThat(shows.opened)
        .containsExactly(
          TestShowPresenterFactory.Opened(
            podcast = podcasts[1],
            catalog = podcasts,
            source = ShowSource.Browse,
          ),
        )
      assertThat(backstack.backstack).containsExactly(presenter, shows.presenter)
    }
  }

  @Test
  fun `show header keeps topic or search context`() = runTest {
    listOf(DiscoveryTopic.NATURE, null).forEach { topic ->
      val shows = TestShowPresenterFactory()
      val repository = TestPodcastCatalog()
      val selectedPodcast = podcast("first")
      repository.response = HttpResponse.Success(listOf(selectedPodcast), 200)
      repository.updatePopular(State.Success(listOf(selectedPodcast)))
      val presenter =
        DiscoveryPresenterImpl(
          repository = repository,
          feeds = TestPodcastFeeds(),
          episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
          showPresenterFactory = shows,
          topic = topic,
        )
      val backstack = FakePresenterBackstackScope(presenter)

      presenter.testCatalog(this, backstack) {
        if (topic != null) {
          awaitItem()
        }
        var model = awaitItem()
        if (topic == null) {
          model.query.replaceText("Nature")
          advanceTimeBy(300)
          runCurrent()
          model = expectMostRecentItem()
        }
        model.onPodcastSelected(selectedPodcast)
        val expectedSource = if (topic == null) ShowSource.Search else ShowSource.Topic(topic)
        assertThat(shows.opened.single().source).isEqualTo(expectedSource)
      }
    }
  }

  @Test
  fun `browse resolves the first two shows and search does not request more`() = runTest {
    val repository = TestPodcastCatalog()
    val feeds = TestPodcastFeeds()
    val podcasts =
      listOf(
        podcast("missing"),
        podcast("one").copy(feedUrl = "one"),
        podcast("duplicate").copy(feedUrl = " one "),
        podcast("two").copy(feedUrl = "two"),
        podcast("three").copy(feedUrl = "three"),
      )
    repository.updatePopular(State.Success(podcasts))
    repository.response = HttpResponse.Success(listOf(podcasts.last()), 200)
    val presenter =
      DiscoveryPresenterImpl(
        repository = repository,
        feeds = feeds,
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        showPresenterFactory = TestShowPresenterFactory(),
      )
    presenter.testCatalog(this) {
      val initial = awaitItem()
      assertThat(feeds.requested).containsExactly("one")
      assertThat(repository.lookedUp).containsExactly("missing")
      initial.query.replaceText("search")
      awaitItem()
      advanceTimeBy(300)
      runCurrent()
      assertThat(expectMostRecentItem().episodes.episodes).isEmpty()
      assertThat(feeds.requested).containsExactly("one")
      assertThat(repository.lookedUp).containsExactly("missing")
    }
  }

  private suspend fun DiscoveryPresenterImpl.testCatalog(
    scope: TestScope,
    backstack: FakePresenterBackstackScope = FakePresenterBackstackScope(),
    validate: suspend ReceiveTurbine<DiscoveryPresenterImpl.Model>.() -> Unit,
  ) {
    ComposePresenter<Unit, DiscoveryPresenterImpl.Model> { present(it) }
      .withPresenterBackstackScope(backstack)
      .test(scope, validate = validate)
  }

  private fun podcast(id: String) =
    DiscoveredPodcast(id, "Podcast $id", "Publisher", null, null, null)
}
