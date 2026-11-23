package software.ralf.storymile.database

import app.cash.turbine.test
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class PodcastDatabaseTest {
  @Test
  fun `deleting a show removes its episodes without changing other shows`() = runTest {
    DatabaseFixture().use { database ->
      val podcast = DatabaseFixture.podcast()
      val other = podcast.copy(feedUrl = "https://feeds.test/other.xml")
      database.insert(podcast)
      database.insert(other)
      database.podcasts.test {
        assertThat(awaitItem()).isEqualTo(listOf(other, podcast))
        database.delete(podcast.feedUrl)
        assertThat(awaitItem()).isEqualTo(listOf(other))
        database.delete(podcast.feedUrl)
        assertThat(database.podcasts.first()).isEqualTo(listOf(other))

        val followedAgain = podcast.copy(episodes = podcast.episodes.take(1))
        database.insert(followedAgain)
        assertThat(awaitItem()).isEqualTo(listOf(other, followedAgain))
      }
    }
  }

  @Test
  fun `flows preserve metadata and order while replacing one podcast atomically`() = runTest {
    DatabaseFixture().use { database ->
      val podcast = DatabaseFixture.podcast()
      val other = podcast.copy(feedUrl = "https://feeds.test/another.xml", title = "Another show")
      database.podcasts.test {
        assertThat(awaitItem()).isEmpty()

        database.insert(podcast)
        assertThat(awaitItem()).isEqualTo(listOf(podcast))

        database.insert(other)
        assertThat(awaitItem()).isEqualTo(listOf(other, podcast))

        val episode = podcast.episodes.last().copy(title = "Updated episode")
        val replacement =
          podcast.copy(
            title = "Updated show",
            description = null,
            author = null,
            artworkUrl = null,
            link = null,
            episodes = listOf(episode),
          )
        database.insert(
          replacement.copy(episodes = listOf(episode, episode.copy(title = "Duplicate"))),
        )
        assertThat(awaitItem()).isEqualTo(listOf(other, replacement))

        val withoutEpisodes = replacement.copy(episodes = emptyList())
        database.insert(withoutEpisodes)
        assertThat(awaitItem()).isEqualTo(listOf(other, withoutEpisodes))
      }
    }
  }

  @Test
  fun `replacing a large feed keeps saved and queued episodes across the removed range`() =
    runTest {
      DatabaseFixture().use { database ->
        val podcast = DatabaseFixture.podcast()
        val template = podcast.episodes.first()
        val episodes =
          List(1_005) { index ->
            template.copy(
              id = "episode-$index",
              enclosure = template.enclosure.copy(url = "https://media.test/$index.mp3"),
            )
          }
        database.insert(podcast.copy(episodes = episodes))
        val queued = DatabaseFixture.queuedEpisode(podcast = podcast, episode = episodes.first())
        val savedEpisode =
          DatabaseFixture.savedEpisode(podcast = podcast, episode = episodes.last())
        queue.enqueue(queued)
        saved.save(savedEpisode)

        val refreshed =
          podcast.copy(episodes = listOf(episodes[2], episodes[1].copy(title = "Updated")))
        database.insert(refreshed)
        database.insert(refreshed)

        assertThat(database.podcasts.first()).isEqualTo(listOf(refreshed))
        assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(queued))
        assertThat(saved.savedEpisodes.first()).isEqualTo(listOf(savedEpisode))
      }
    }

  @Test
  fun `memory databases are isolated and start empty after scope recreation`() = runTest {
    val podcast = DatabaseFixture.podcast()
    DatabaseFixture().use { first ->
      first.insert(podcast)
      DatabaseFixture().use { second ->
        assertThat(second.podcasts.first()).isEmpty()
        val different = podcast.copy(title = "Different user")
        second.insert(different)
        assertThat(second.podcasts.first()).isEqualTo(listOf(different))
        assertThat(first.podcasts.first()).isEqualTo(listOf(podcast))
      }
    }
    DatabaseFixture().use { reopened -> assertThat(reopened.podcasts.first()).isEmpty() }
  }

  @Test
  fun `scope exit cancels active collectors and rejects retained database access`() = runTest {
    val fixture = DatabaseFixture()
    fixture.use { database ->
      val started = CompletableDeferred<Unit>()
      val collection = async {
        assertFailure { database.podcasts.collect { started.complete(Unit) } }
          .isInstanceOf<CancellationException>()
      }
      started.await()
      fixture.close()
      collection.await()

      assertFailure { database.insert(DatabaseFixture.podcast()) }
        .isInstanceOf<CancellationException>()
      assertFailure { database.delete(DatabaseFixture.podcast().feedUrl) }
        .isInstanceOf<CancellationException>()
      assertFailure { database.podcasts.first() }.isInstanceOf<CancellationException>()
    }
  }
}
