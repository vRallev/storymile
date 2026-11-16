package software.ralf.storymile.database

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem

class DatabasePersistenceTest {
  @Test
  fun `disk data survives reopening and stays separate from other users and memory databases`() =
    runTest {
      val directory =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "storymile-database-${Random.nextLong().toULong()}"
      FileSystem.SYSTEM.createDirectories(directory)
      val path = (directory / "podcasts.db").toString()
      val podcast = DatabaseFixture.podcast()
      val queued =
        podcast.episodes.map { DatabaseFixture.queuedEpisode(podcast = podcast, episode = it) }
      val first = queued.first()
      val last = queued.last()
      val reordered = listOf(last) + queued.dropLast(1)
      try {
        DatabaseFixture(path).use { database ->
          database.insert(podcast)
          queued.forEach { queue.enqueue(it) }
          queue.moveInQueue(
            feedUrl = last.feedUrl,
            episodeId = last.episode.id,
            targetFeedUrl = first.feedUrl,
            targetEpisodeId = first.episode.id,
          )
        }
        DatabaseFixture().use { memory ->
          assertThat(memory.podcasts.first()).isEmpty()
          assertThat(queue.queuedEpisodes.first()).isEmpty()
          memory.insert(podcast.copy(title = "Fake mode"))
        }
        DatabaseFixture((directory / "other-user.db").toString()).use { other ->
          assertThat(other.podcasts.first()).isEmpty()
          assertThat(queue.queuedEpisodes.first()).isEmpty()
        }
        DatabaseFixture(path).use { reopened ->
          assertThat(reopened.podcasts.first()).isEqualTo(listOf(podcast))
          assertThat(queue.queuedEpisodes.first()).isEqualTo(reordered)
          reopened.delete(podcast.feedUrl)
        }
        DatabaseFixture(path).use { reopened ->
          assertThat(reopened.podcasts.first()).isEmpty()
          assertThat(queue.queuedEpisodes.first()).isEqualTo(reordered)
        }
      } finally {
        FileSystem.SYSTEM.deleteRecursively(directory)
      }
    }
}
