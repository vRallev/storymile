package software.ralf.storymile.podcast

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.database.PodcastDatabase

class SavedShowRepositoryImplTest {
  @Test
  fun `the first collector loads saved shows once for the user scope`() = runTest {
    val database = GatedDatabase(initial = listOf(show()))
    val repository: SavedShowRepository =
      SavedShowRepositoryImpl(database = database, coroutineScope = backgroundScope)

    runCurrent()
    assertThat(repository.shows.value).isNull()
    assertThat(database.reads).isEqualTo(0)

    repository.shows.filterNotNull().test {
      assertThat(awaitItem()).containsExactly(show())
      assertThat(repository.shows.filterNotNull().first()).containsExactly(show())
      assertThat(database.reads).isEqualTo(1)
    }
    assertThat(repository.shows.filterNotNull().first()).containsExactly(show())
    assertThat(database.reads).isEqualTo(1)
  }

  @Test
  fun `a read failure keeps the loading state until a retry succeeds`() = runTest {
    val database = GatedDatabase(initial = listOf(show()))
    database.readFailure = IllegalStateException("Cannot read")
    val repository: SavedShowRepository =
      SavedShowRepositoryImpl(database = database, coroutineScope = backgroundScope)

    repository.shows.test {
      assertThat(awaitItem()).isNull()
      runCurrent()
      assertThat(database.reads).isEqualTo(1)
      expectNoEvents()

      database.readFailure = null
      advanceTimeBy(5.seconds)
      assertThat(awaitItem().orEmpty()).containsExactly(show())
      assertThat(database.reads).isEqualTo(2)
    }
  }

  @Test
  fun `a queued remove waits for the save and observers see the final state`() = runTest {
    val database = GatedDatabase()
    val repository: SavedShowRepository =
      SavedShowRepositoryImpl(database = database, coroutineScope = backgroundScope)
    val show = show()
    repository.shows.filterNotNull().test {
      assertThat(awaitItem()).isEmpty()
      repository.save(show)
      repository.remove(show.feedUrl)
      database.insertStarted.await()
      assertThat(repository.shows.value.orEmpty()).isEmpty()
      assertThat(database.deleteStarted.isCompleted).isFalse()

      database.finishInsert.complete(Unit)
      assertThat(awaitItem()).containsExactly(show)
      database.finishDelete.complete(Unit)
      assertThat(awaitItem()).isEmpty()
    }
  }

  @Test
  fun `a failed write leaves the saved list intact and the next write still runs`() = runTest {
    val database = GatedDatabase()
    val repository: SavedShowRepository =
      SavedShowRepositoryImpl(database = database, coroutineScope = backgroundScope)
    val show = show()
    repository.save(show)
    database.insertStarted.await()
    database.finishInsert.completeExceptionally(IllegalStateException("Cannot save"))
    runCurrent()
    assertThat(repository.shows.filterNotNull().first()).isEmpty()

    database.finishInsert = CompletableDeferred(Unit)
    repository.save(show)
    assertThat(repository.shows.filterNotNull().first { it.isNotEmpty() }).containsExactly(show)
  }

  @Test
  fun `user scope exit cancels writes in progress and in the queue`() = runTest {
    val database = GatedDatabase()
    val owner =
      CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
    val repository: SavedShowRepository =
      SavedShowRepositoryImpl(database = database, coroutineScope = owner)
    assertThat(repository.shows.filterNotNull().first()).isEmpty()
    val show = show()
    repository.save(show)
    repository.remove(show.feedUrl)
    database.insertStarted.await()
    owner.cancel()
    database.finishInsert.complete(Unit)
    runCurrent()

    assertThat(repository.shows.value.orEmpty()).isEmpty()
    assertThat(database.deleteStarted.isCompleted).isFalse()
  }

  private fun show() =
    PodcastFeed(
      feedUrl = "https://feeds.test/show",
      title = "Saved show",
      description = null,
      author = null,
      artworkUrl = null,
      link = null,
      episodes = emptyList(),
    )

  private class GatedDatabase(initial: List<PodcastFeed> = emptyList()) : PodcastDatabase {
    private val saved = MutableStateFlow(initial)
    val insertStarted = CompletableDeferred<Unit>()
    val deleteStarted = CompletableDeferred<Unit>()
    val finishDelete = CompletableDeferred<Unit>()
    var finishInsert = CompletableDeferred<Unit>()
    var readFailure: Exception? = null
    var reads = 0
      private set

    override val podcasts = flow {
      reads++
      readFailure?.let { throw it }
      emitAll(saved)
    }

    override suspend fun insert(podcast: PodcastFeed) {
      insertStarted.complete(Unit)
      finishInsert.await()
      saved.update { saved ->
        saved.filterNot { it.feedUrl == podcast.feedUrl } + podcast
      }
    }

    override suspend fun delete(feedUrl: String) {
      deleteStarted.complete(Unit)
      finishDelete.await()
      saved.update { saved -> saved.filterNot { it.feedUrl == feedUrl } }
    }
  }
}
