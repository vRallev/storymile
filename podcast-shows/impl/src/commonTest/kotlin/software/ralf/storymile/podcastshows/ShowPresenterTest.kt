package software.ralf.storymile.podcastshows

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State as CatalogState
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.testing.FakeEpisodeDetailsPresenterFactory
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository.State as FeedState

class ShowPresenterTest {
  @Test
  fun `episode back preserves the selected show and updates its metadata while open`() = runTest {
    val catalog = TestPodcastCatalog()
    val feeds = TestPodcastFeeds()
    feeds.update("two", FeedState.Success(feed("two")))
    val presenter = presenter(catalog = catalog, feeds = feeds)
    val outerBackstack = FakePresenterBackstackScope()
    val outerRoot = outerBackstack.backstack.single()
    outerBackstack.push(presenter)

    presenter.withBackstack(outerBackstack).test(this) {
      val initial = awaitItem()
      assertThat(initial.backstack.size).isEqualTo(1)
      (initial.content.pane as ShowListPane.Discovery).onSelect(podcast("two"))
      val selected = awaitItem()
      selected.content.onToggleDescription()
      val expanded = awaitItem()
      expanded.content.episodes.episodes.first().onClick()

      val opened = awaitItem()
      assertThat(opened.backstack.size).isEqualTo(2)
      assertThat(opened.content.podcast.id).isEqualTo("two")
      assertThat(opened.content.descriptionExpanded).isTrue()
      assertThat((opened.backstack.last() as EpisodeModel).details.episode.title)
        .isEqualTo("Episode one")
      assertThat(outerBackstack.backstack).containsExactly(outerRoot, presenter)

      val enriched = podcast("two").copy(description = "The selected show's description.")
      catalog.updateLookup(id = "two", state = CatalogState.Success(listOf(enriched)))
      val updated = awaitItem()
      assertThat(updated.content.podcast).isEqualTo(enriched.preview())
      assertThat(updated.content.descriptionExpanded).isTrue()
      (updated.backstack.last() as EpisodeModel).onBack()

      val returned = awaitItem()
      assertThat(returned.backstack.size).isEqualTo(1)
      assertThat(returned.content.podcast).isEqualTo(enriched.preview())
      assertThat(returned.content.descriptionExpanded).isTrue()
      assertThat(returned.content.episodes.episodes.map { it.details.episode.title })
        .containsExactly("Episode one", "Episode two")
      returned.onBack()
      assertThat(outerBackstack.backstack).containsExactly(outerRoot)
    }
  }

  @Test
  fun `selecting another episode replaces the detail entry and back returns to the show`() =
    runTest {
      val feeds = TestPodcastFeeds()
      feeds.update("one", FeedState.Success(feed("one")))
      val presenter = presenter(catalog = TestPodcastCatalog(), feeds = feeds)
      val outerBackstack = FakePresenterBackstackScope()

      presenter.withBackstack(outerBackstack).test(this) {
        awaitItem().content.episodes.episodes.first().onClick()
        val first = awaitItem()
        assertThat(first.backstack.size).isEqualTo(2)
        first.content.episodes.episodes.last().onClick()

        val second = awaitItem()
        assertThat(second.backstack.size).isEqualTo(2)
        assertThat((second.backstack.last() as EpisodeModel).details.episode.title)
          .isEqualTo("Episode two")
        second.onBack()

        val returned = awaitItem()
        assertThat(returned.backstack.size).isEqualTo(1)
        assertThat(returned.content.podcast.id).isEqualTo("one")
        assertThat(outerBackstack.backstack.size).isEqualTo(1)
      }
    }

  @Test
  fun `show selection observes the selected feed and drops the previous episodes`() = runTest {
    val feeds = TestPodcastFeeds()
    val first = podcast("one").copy(feedUrl = "one")
    val second = podcast("two").copy(feedUrl = "two")
    val presenter =
      ShowPresenterImpl(
        catalog = TestPodcastCatalog(),
        feeds = feeds,
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        savedShows = TestSavedShowRepository(),
        selection =
          ShowSelection.Discovery(initialPodcast = first, podcasts = listOf(first, second)),
        source = ShowSource.Browse,
      )
    presenter.withBackstack(FakePresenterBackstackScope()).test(this) {
      val initial = awaitItem().content
      assertThat(initial.episodes.loading).isEqualTo(true)
      assertThat(feeds.requested.distinct()).containsExactly("one")
      (initial.pane as ShowListPane.Discovery).onSelect(second)
      val switched = awaitItem().content
      assertThat(switched.podcast.id).isEqualTo(second.id)
      assertThat(switched.episodes.episodes).isEmpty()
      assertThat(feeds.requested.distinct()).containsExactly("one", "two")
      feeds.update("two", FeedState.Error)
      val failed = awaitItem().content
      assertThat(failed.episodes.failed).isEqualTo(true)
      failed.episodes.onRetry()
      assertThat(feeds.refreshed).containsExactly("two")
      assertThat(awaitItem().content.episodes.loading).isEqualTo(true)
    }
  }

  @Test
  fun `saved shows use stored metadata and episodes when the feed is unavailable`() = runTest {
    val first = feed("one").copy(description = "<p>Stored &amp; readable</p>")
    val second = feed("two")
    val saved = TestSavedShowRepository().apply { publish(listOf(first, second)) }
    val feeds = TestPodcastFeeds().apply { update(first.feedUrl, FeedState.Error) }
    val catalog = TestPodcastCatalog()
    val presenter =
      ShowPresenterImpl(
        catalog = catalog,
        feeds = feeds,
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
        savedShows = saved,
        selection = ShowSelection.Saved(first),
        source = ShowSource.Library,
      )

    presenter.withBackstack(FakePresenterBackstackScope()).test(this) {
      val initial = awaitItem().content
      assertThat(initial.source).isEqualTo(ShowSource.Library)
      assertThat(initial.podcast.description).isEqualTo("Stored & readable")
      assertThat(initial.episodes.episodes.map { it.details.episode.title })
        .containsExactly("Episode one", "Episode two")
      assertThat(initial.followed).isTrue()

      (initial.pane as ShowListPane.Saved).onSelect(second)
      val switched = awaitItem().content
      assertThat(switched.podcast.id).isEqualTo(second.feedUrl)
      switched.onToggleFollow()
      val unfollowed = awaitItem().content
      assertThat(unfollowed.followed).isFalse()
      assertThat((unfollowed.pane as ShowListPane.Saved).shows).containsExactly(first)
      assertThat(unfollowed.episodes.episodes.first().details.feedUrl).isEqualTo(second.feedUrl)
      runCurrent()
      assertThat(feeds.requested).containsExactly(first.feedUrl, second.feedUrl)
      assertThat(catalog.lookedUp).isEmpty()

      val refreshed =
        second.copy(
          feedUrl = "https://feeds.test/redirected",
          episodes = listOf(second.episodes.first().copy(title = "Latest episode")),
        )
      feeds.update(second.feedUrl, FeedState.Success(refreshed))
      val latest = awaitItem().content.episodes.episodes.single().details
      assertThat(latest.episode.title).isEqualTo("Latest episode")
      assertThat(latest.feedUrl).isEqualTo(second.feedUrl)

      feeds.update(second.feedUrl, FeedState.Error)
      runCurrent()
      expectNoEvents()
    }
  }

  private fun ShowPresenterImpl.withBackstack(backstack: FakePresenterBackstackScope) =
    ComposePresenter<Unit, ShowPresenterImpl.Model> { present(it) }
      .withPresenterBackstackScope(backstack)

  private fun presenter(catalog: TestPodcastCatalog, feeds: TestPodcastFeeds) =
    ShowPresenterImpl(
      catalog = catalog,
      feeds = feeds,
      episodeDetailsFactory =
        FakeEpisodeDetailsPresenterFactory { details ->
          val backstack = LocalBackstackScope.requireNotNull()
          EpisodeModel(details = details, onBack = { backstack.pop() })
        },
      savedShows = TestSavedShowRepository(),
      selection =
        ShowSelection.Discovery(
          initialPodcast = podcast("one"),
          podcasts = listOf(podcast("one"), podcast("two")),
        ),
      source = ShowSource.Browse,
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

  private fun feed(id: String) =
    PodcastFeed(
      feedUrl = id,
      title = "Show $id",
      description = null,
      author = null,
      artworkUrl = null,
      link = null,
      episodes =
        listOf("one", "two").map { episode ->
          PodcastEpisode(
            id = episode,
            title = "Episode $episode",
            description = null,
            author = null,
            artworkUrl = null,
            publishedAt = null,
            duration = null,
            enclosure =
              PodcastEnclosure(
                url = "https://audio.test/$episode.mp3",
                type = "audio/mpeg",
                length = null,
              ),
          )
        },
    )

  private data class EpisodeModel(val details: EpisodeDetails, val onBack: () -> Unit) : BaseModel

  private fun DiscoveredPodcast.preview() =
    ShowPreview(
      id = id,
      title = title,
      author = author,
      artworkUrl = artworkUrl,
      description = description,
    )
}
