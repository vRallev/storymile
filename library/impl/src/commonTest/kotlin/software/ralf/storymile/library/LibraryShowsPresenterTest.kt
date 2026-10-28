package software.ralf.storymile.library

import app.cash.turbine.ReceiveTurbine
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.presenter.backstack.nav3.FakePresenterBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.withPresenterBackstackScope
import software.ralf.app.platform.presenter.compose.test
import software.ralf.storymile.episodedetails.testing.FakeEpisodeDetailsPresenterFactory
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed

class LibraryShowsPresenterTest {
  @Test
  fun `followed shows update live and saved episodes open without catalog or feed requests`() =
    runTest {
      val database = TestPodcastDatabase()
      val backstack = FakePresenterBackstackScope()
      presenter(database).withPresenterBackstackScope(backstack).test(this) {
        assertThat(awaitModel { !it.loading }.shows).isEmpty()
        database.insert(feed("one"))
        val first = awaitModel { it.shows.size == 1 }
        assertThat(first.details?.podcast?.description).isEqualTo("Saved description")
        assertThat(first.details?.episodes?.episodes?.single()?.details?.episode?.title)
          .isEqualTo("Episode one")
        database.insert(feed("two"))
        val both = awaitModel { it.shows.size == 2 }
        both.onSelectShow(both.shows.last())
        val selected = awaitModel { it.details?.podcast?.id == "two" }
        assertThat(selected.detailOpen).isTrue()
        selected.details!!.episodes.episodes.single().onClick()
        assertThat(backstack.backstack.size).isEqualTo(2)
        selected.onBack()
        assertThat(awaitModel { !it.detailOpen }.details?.podcast?.id).isEqualTo("two")
        database.delete("two")
        val remaining = awaitModel { it.shows.size == 1 }
        assertThat(remaining.details?.podcast?.id).isEqualTo("one")
        database.delete("one")
        assertThat(awaitModel { it.shows.isEmpty() && !it.detailOpen }.details).isEqualTo(null)
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `failed unfollow preserves the show and retry removes it after the write completes`() =
    runTest {
      val database = TestPodcastDatabase()
      database.insert(feed("one"))
      database.writeFailure = IllegalStateException("write failed")
      presenter(database).withPresenterBackstackScope(FakePresenterBackstackScope()).test(this) {
        awaitModel { !it.loading }.onUnfollow("one")
        val failed = awaitModel { it.unfollowFailed == "one" }
        assertThat(failed.shows.map { it.id }).containsExactly("one")
        assertThat(failed.details).isNotNull().given { assertThat(it.followFailed).isTrue() }
        database.writeFailure = null
        database.finishWrite = CompletableDeferred()
        failed.onUnfollow("one")
        val saving = awaitModel { "one" in it.updating }
        assertThat(saving.details!!.followEnabled).isFalse()
        assertThat(saving.shows.map { it.id }).containsExactly("one")
        saving.onUnfollow("one")
        database.finishWrite!!.complete(Unit)
        awaitModel { it.shows.isEmpty() && it.updating.isEmpty() }
        assertThat(database.saved.value).isEmpty()
        cancelAndIgnoreRemainingEvents()
      }
    }

  @Test
  fun `a database read error offers retry instead of the empty library`() = runTest {
    val database = TestPodcastDatabase()
    database.insert(feed("one"))
    database.readFailure = IllegalStateException("read failed")
    presenter(database).withPresenterBackstackScope(FakePresenterBackstackScope()).test(this) {
      val failed = awaitModel { it.failed }
      assertThat(failed.loading).isFalse()
      database.readFailure = null
      failed.onRetry()
      val ready = awaitModel { it.shows.isNotEmpty() }
      assertThat(ready.failed).isFalse()
      assertThat(ready.shows.map { it.id }).containsExactly("one")
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun presenter(database: TestPodcastDatabase) =
    LibraryShowsPresenter(
      database = database,
      episodeDetailsFactory = FakeEpisodeDetailsPresenterFactory(),
    )

  private suspend fun ReceiveTurbine<LibraryShowsPresenter.Model>.awaitModel(
    predicate: (LibraryShowsPresenter.Model) -> Boolean,
  ): LibraryShowsPresenter.Model {
    while (true) {
      val model = awaitItem()
      if (predicate(model)) {
        return model
      }
    }
  }

  private fun feed(id: String) =
    PodcastFeed(
      feedUrl = id,
      title = "Show $id",
      description = "<p>Saved description</p>",
      author = "Author",
      artworkUrl = null,
      link = null,
      episodes =
        listOf(
          PodcastEpisode(
            id = id,
            title = "Episode $id",
            description = null,
            author = null,
            artworkUrl = null,
            publishedAt = null,
            duration = null,
            enclosure =
              PodcastEnclosure(
                url = "https://audio.test/$id.mp3",
                type = "audio/mpeg",
                length = null,
              ),
          ),
        ),
    )
}
