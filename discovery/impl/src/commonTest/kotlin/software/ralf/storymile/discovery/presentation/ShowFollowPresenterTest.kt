package software.ralf.storymile.discovery.presentation

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
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
  fun `follow stores the feed once and unfollow removes it`() = runTest {
    val database = TestPodcastDatabase()
    val finishWrite = CompletableDeferred<Unit>()
    database.finishWrite = finishWrite
    presenter(database).test(this) {
      val ready = awaitModel { it.followEnabled }
      ready.onToggleFollow()
      ready.onToggleFollow()
      assertThat(awaitItem().followEnabled).isFalse()
      runCurrent()
      assertThat(database.writes).isEqualTo(1)
      assertThat(database.saved.value).isEmpty()

      finishWrite.complete(Unit)
      val followed = awaitModel { it.followed && it.followEnabled }
      assertThat(database.saved.value).containsExactly(feed("one"))
      followed.onToggleFollow()
      awaitModel { !it.followed && it.followEnabled }
      assertThat(database.saved.value).isEmpty()
      assertThat(database.writes).isEqualTo(2)
    }
  }

  @Test
  fun `saved shows can be unfollowed after reopening when the feed is unavailable`() = runTest {
    val database = TestPodcastDatabase()
    database.insert(feed("one"))
    val feeds = TestPodcastFeeds().apply { update("one", FeedState.Error) }
    presenter(database = database, feeds = feeds).test(this) {
      val followed = awaitModel { it.followed }
      assertThat(followed.followEnabled).isTrue()
      followed.onToggleFollow()
      awaitModel { !it.followed }
      assertThat(database.saved.value).isEmpty()
    }
    presenter(database).test(this) {
      assertThat(awaitModel { it.followEnabled }.followed).isFalse()
    }
  }

  @Test
  fun `failed writes keep the saved state and can be retried in either direction`() = runTest {
    val database = TestPodcastDatabase()
    presenter(database).test(this) {
      var model = awaitModel { it.followEnabled }
      listOf(false, true).forEach { initiallyFollowed ->
        database.writeFailure = IllegalStateException("Disk unavailable")
        model.onToggleFollow()
        model = awaitModel { it.followFailed }
        assertThat(model.followed).isEqualTo(initiallyFollowed)
        assertThat(database.saved.value.isNotEmpty()).isEqualTo(initiallyFollowed)
        database.writeFailure = null
        model.onToggleFollow()
        model = awaitModel { it.followed != initiallyFollowed && it.followEnabled }
        assertThat(model.followFailed).isFalse()
      }
    }
  }

  @Test
  fun `switching shows during a save keeps their follow states independent`() = runTest {
    val database = TestPodcastDatabase()
    val finishWrite = CompletableDeferred<Unit>()
    database.finishWrite = finishWrite
    presenter(database).test(this) {
      val ready = awaitModel { it.followEnabled }
      ready.onToggleFollow()
      awaitItem().onPodcastSelected(podcast("two"))
      val second = awaitModel { it.podcast.id == "two" && it.followEnabled }
      assertThat(second.followed).isFalse()
      finishWrite.complete(Unit)
      runCurrent()
      assertThat(database.saved.value).containsExactly(feed("one"))
      second.onPodcastSelected(podcast("one"))
      assertThat(awaitModel { it.podcast.id == "one" }.followed).isTrue()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `lookup supplies the feed URL and existing saves are observed`() = runTest {
    val database = TestPodcastDatabase()
    val catalog = TestPodcastCatalog()
    presenter(
        database = database,
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
        assertThat(database.saved.value).containsExactly(feed("one"))
        database.delete("one")
        assertThat(awaitModel { !it.followed }.followEnabled).isTrue()
      }
  }

  @Test
  fun `database read failure can be retried without claiming a show was saved`() = runTest {
    val database = TestPodcastDatabase()
    database.readFailure = IllegalStateException("Database unavailable")
    presenter(database).test(this) {
      val failed = awaitModel { it.followFailed }
      assertThat(failed.followed).isFalse()
      database.readFailure = null
      failed.onToggleFollow()
      awaitModel { it.followEnabled && !it.followFailed }.onToggleFollow()
      awaitModel { it.followed && it.followEnabled }
      assertThat(database.saved.value).containsExactly(feed("one"))
    }
  }

  private fun presenter(
    database: TestPodcastDatabase,
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
        database = database,
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
