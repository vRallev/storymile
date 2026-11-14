package software.ralf.storymile.library

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.episodedetails.testing.FakeEpisodeDetailsPresenterFactory
import software.ralf.storymile.library.FollowedShowsPresenter.Model
import software.ralf.storymile.library.FollowedShowsPresenter.State
import software.ralf.storymile.podcast.PodcastFeed

class FollowedShowsPresenterTest {
  @Test
  fun `a failed read can be retried without showing a false empty state`() = runTest {
    val database = FakePodcastDatabase()
    val saved = show(title = "Field Notes", feedUrl = "https://feeds.test/field-notes")
    database.insert(saved)
    database.readFailure = IllegalStateException("Database unavailable")

    createPresenter(database).test(this, true) {
      val failed = awaitModel { it.state == State.ReadFailed }
      database.readFailure = null
      failed.onRetry()
      val loaded = awaitModel { it.state is State.Loaded }
      assertThat(loaded.state).isEqualTo(State.Loaded(listOf(saved)))
    }
  }

  @Test
  fun `inserting a show keeps the selected feed and removing it returns to the list`() = runTest {
    val database = FakePodcastDatabase()
    val first = show(title = "The Quiet Current", feedUrl = "https://feeds.test/quiet-current")
    val second = show(title = "field notes", feedUrl = "https://feeds.test/field-notes")
    database.insert(first)

    createPresenter(database).test(this, true) {
      val initial = awaitModel { it.state == State.Loaded(listOf(first)) }
      initial.onSelectShow(first.feedUrl)
      assertThat(awaitModel { it.detailRequested }.selectedShow).isEqualTo(first)

      database.insert(second)
      val sorted = awaitModel { it.state == State.Loaded(listOf(second, first)) }
      assertThat(sorted.selectedShow).isEqualTo(first)
      sorted.onUnfollow(first.feedUrl)

      val fallback = awaitModel { it.state == State.Loaded(listOf(second)) }
      assertThat(fallback.detailRequested).isFalse()
      assertThat(fallback.selectedShow).isEqualTo(second)
      fallback.onSelectShow(second.feedUrl)
      assertThat(awaitModel { it.detailRequested }.detailRequested).isTrue()
    }
  }

  private fun createPresenter(database: FakePodcastDatabase) =
    FollowedShowsPresenter(
        database = database,
        episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
      )
      .withPresenterBackstackScope(FakePresenterBackstackScope())

  private suspend fun ReceiveTurbine<Model>.awaitModel(predicate: (Model) -> Boolean): Model {
    while (true) {
      val model = awaitItem()
      if (predicate(model)) {
        return model
      }
    }
  }

  private fun show(title: String, feedUrl: String) =
    PodcastFeed(
      feedUrl = feedUrl,
      title = title,
      description = null,
      author = null,
      artworkUrl = null,
      link = null,
      episodes = emptyList(),
    )
}
