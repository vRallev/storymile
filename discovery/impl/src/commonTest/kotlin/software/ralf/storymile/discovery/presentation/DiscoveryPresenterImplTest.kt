package software.ralf.storymile.discovery.presentation

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import software.ralf.storymile.discovery.PodcastDiscoveryService
import software.ralf.storymile.http.HttpResponse

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryPresenterImplTest {
  @Test
  fun `browsing loads popular podcasts and retry recovers from failure`() = runTest {
    val service = TestCatalog()
    service.response = HttpResponse.Error.Server(503)
    val presenter = DiscoveryPresenterImpl(service)

    presenter.testCatalog(this) {
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      val failed = awaitItem()
      assertThat(failed.state).isEqualTo(CatalogState.Error)
      service.response = HttpResponse.Success(listOf(podcast("popular")), 200)
      failed.onRetry()
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      assertThat(awaitItem().state).isEqualTo(CatalogState.Ready(listOf(podcast("popular"))))
      assertThat(service.popularRequests).isEqualTo(2)
      assertThat(service.queries).isEmpty()
    }
  }

  @Test
  fun `typing replaces results after debounce and clearing returns to featured`() = runTest {
    val service = TestCatalog()
    val presenter = DiscoveryPresenterImpl(service)

    presenter.testCatalog(this) {
      awaitItem()
      val initial = awaitItem()
      initial.query.replaceText(" sci ")
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      advanceTimeBy(200)
      initial.query.replaceText(" science ")
      runCurrent()
      advanceTimeBy(299)
      runCurrent()
      assertThat(service.queries).isEmpty()
      advanceTimeBy(1)
      runCurrent()
      val results = expectMostRecentItem()
      assertThat(results.state).isInstanceOf<CatalogState.Ready>()
      assertThat(service.queries).containsExactly("science")

      results.query.clearText()
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      assertThat(awaitItem().state).isInstanceOf<CatalogState.Ready>()
      assertThat(service.popularRequests).isEqualTo(2)
    }
  }

  @Test
  fun `superseded request cannot replace newer search results`() = runTest {
    val service = TestCatalog()
    val oldResponse = CompletableDeferred<HttpResponse<List<DiscoveredPodcast>>>()
    service.pending["old"] = oldResponse
    val presenter = DiscoveryPresenterImpl(service)

    presenter.testCatalog(this) {
      awaitItem()
      val initial = awaitItem()
      initial.query.replaceText("old")
      awaitItem()
      advanceTimeBy(300)
      runCurrent()
      assertThat(service.queries).containsExactly("old")

      service.response = HttpResponse.Success(listOf(podcast("new")), 200)
      initial.query.replaceText("new")
      runCurrent()
      advanceTimeBy(300)
      runCurrent()
      assertThat(expectMostRecentItem().state).isEqualTo(CatalogState.Ready(listOf(podcast("new"))))

      oldResponse.complete(HttpResponse.Success(listOf(podcast("old")), 200))
      runCurrent()
      expectNoEvents()
      assertThat(service.queries).containsExactly("old", "new")
    }
  }

  @Test
  fun `topic filters search their keywords and empty responses stay empty`() = runTest {
    val service = TestCatalog()
    service.response = HttpResponse.Success(emptyList(), 200)
    val presenter = DiscoveryPresenterImpl(service, topic = DiscoveryTopic.NATURE)

    presenter.testCatalog(this) {
      awaitItem()
      val initial = awaitItem()
      assertThat(initial.state).isEqualTo(CatalogState.Ready(emptyList()))
      assertThat(service.queries).containsExactly("Nature")
      initial.onFilterSelected(DiscoveryTopic.Filter.OCEANS)
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      val filtered = awaitItem()
      assertThat(filtered.selectedFilter).isEqualTo(DiscoveryTopic.Filter.OCEANS)
      assertThat(service.queries).containsExactly("Nature", "Nature Oceans")

      filtered.onFilterSelected(DiscoveryTopic.Filter.ALL_NATURE)
      assertThat(awaitItem().state).isEqualTo(CatalogState.Loading)
      assertThat(awaitItem().selectedFilter).isEqualTo(DiscoveryTopic.Filter.ALL_NATURE)
      assertThat(service.queries).containsExactly("Nature", "Nature Oceans", "Nature")
      assertThat(service.popularRequests).isEqualTo(0)
    }
  }

  @Test
  fun `opening a show preserves the response order and back returns to the catalog`() = runTest {
    val service = TestCatalog()
    val podcasts = listOf(podcast("first"), podcast("second"))
    service.response = HttpResponse.Success(podcasts, 200)
    val presenter = DiscoveryPresenterImpl(service)
    val backstack = FakePresenterBackstackScope(presenter)

    presenter.testCatalog(this, backstack) {
      awaitItem()
      val model = awaitItem()
      model.onPodcastSelected(podcasts[1])
      val show = backstack.backstack.last()
      assertThat(show).isInstanceOf<ShowPresenter>()
      // ShowPresenter's model is renderer-only; inspect it through its presenter output.
      (show as ShowPresenter).withPresenterBackstackScope(backstack).test(this@runTest) {
        val detail = awaitItem()
        assertThat(detail.podcast).isEqualTo(podcasts[1])
        assertThat(detail.podcasts).isEqualTo(podcasts)
        assertThat(detail.source).isEqualTo(ShowPresenter.Source.Browse)
        detail.onPodcastSelected(podcasts[0])
        assertThat(awaitItem().podcast).isEqualTo(podcasts[0])
        detail.onBack()
        assertThat(backstack.backstack).containsExactly(presenter)
      }
    }
  }

  @Test
  fun `show header keeps topic or search context`() = runTest {
    listOf(DiscoveryTopic.NATURE, null).forEach { topic ->
      val service = TestCatalog()
      val selectedPodcast = podcast("first")
      service.response = HttpResponse.Success(listOf(selectedPodcast), 200)
      val presenter = DiscoveryPresenterImpl(service, topic = topic)
      val backstack = FakePresenterBackstackScope(presenter)

      presenter.testCatalog(this, backstack) {
        awaitItem()
        var model = awaitItem()
        if (topic == null) {
          model.query.replaceText("Nature")
          advanceTimeBy(300)
          runCurrent()
          model = expectMostRecentItem()
        }
        model.onPodcastSelected(selectedPodcast)
        val show = backstack.backstack.last() as ShowPresenter
        show.withPresenterBackstackScope(backstack).test(this@runTest) {
          val expectedSource =
            if (topic == null) ShowPresenter.Source.Search else ShowPresenter.Source.Topic(topic)
          assertThat(awaitItem().source).isEqualTo(expectedSource)
        }
      }
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

  private class TestCatalog : PodcastDiscoveryService {
    var response: HttpResponse<List<DiscoveredPodcast>> = HttpResponse.Success(emptyList(), 200)
    val pending = mutableMapOf<String, CompletableDeferred<HttpResponse<List<DiscoveredPodcast>>>>()
    val queries = mutableListOf<String>()
    var popularRequests = 0

    override suspend fun popular(): HttpResponse<List<DiscoveredPodcast>> {
      popularRequests++
      return response
    }

    override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> {
      queries += query
      return pending[query]?.await() ?: response
    }
  }
}
