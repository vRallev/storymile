package software.ralf.storymile.database

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class EpisodeQueueDatabaseTest {
  @Test
  fun `the queue keeps insertion order independently of followed shows`() = runTest {
    DatabaseFixture().use { database ->
      val podcast = DatabaseFixture.podcast()
      val first =
        DatabaseFixture.queuedEpisode(podcast = podcast, episode = podcast.episodes.first())
      val second =
        DatabaseFixture.queuedEpisode(podcast = podcast, episode = podcast.episodes.last())
      val other = first.copy(feedUrl = "https://feeds.test/other.xml")
      queue.queuedEpisodes.test {
        assertThat(awaitItem()).isEmpty()

        queue.enqueue(first)
        assertThat(awaitItem()).isEqualTo(listOf(first))

        queue.enqueue(second)
        assertThat(awaitItem()).isEqualTo(listOf(first, second))

        queue.enqueue(first.copy(podcastTitle = "Duplicate"))
        queue.enqueue(other)
        assertThat(awaitItem()).isEqualTo(listOf(first, second, other))

        database.insert(podcast)
        database.delete(podcast.feedUrl)
        assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(first, second, other))

        queue.removeFromQueue(feedUrl = first.feedUrl, episodeId = first.episode.id)
        assertThat(awaitItem()).isEqualTo(listOf(second, other))

        queue.enqueue(first)
        assertThat(awaitItem()).isEqualTo(listOf(second, other, first))
      }
    }
  }
}
