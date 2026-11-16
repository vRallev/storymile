package software.ralf.storymile.database

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SavedEpisodeDatabaseTest {
  @Test
  fun `saved episodes are kept once per feed and episode in newest first order`() = runTest {
    DatabaseFixture().use { database ->
      val podcast = DatabaseFixture.podcast()
      val episode = podcast.episodes.last()
      val first = DatabaseFixture.savedEpisode(podcast = podcast, episode = episode)
      val second = first.copy(feedUrl = "https://feeds.test/other.xml")

      saved.savedEpisodes.test {
        assertThat(awaitItem()).isEmpty()

        saved.save(first)
        assertThat(awaitItem()).isEqualTo(listOf(first))

        saved.save(second)
        assertThat(awaitItem()).isEqualTo(listOf(second, first))

        saved.save(first)
        assertThat(saved.savedEpisodes.first()).isEqualTo(listOf(second, first))
        assertThat(database.podcasts.first()).isEmpty()

        saved.removeSavedEpisode(feedUrl = first.feedUrl, episodeId = first.episode.id)
        assertThat(awaitItem()).isEqualTo(listOf(second))

        saved.save(first)
        assertThat(awaitItem()).isEqualTo(listOf(first, second))
      }
    }
  }

  @Test
  fun `feed updates keep saved episodes and removing a save keeps the queue entry`() = runTest {
    DatabaseFixture().use { database ->
      val podcast = DatabaseFixture.podcast()
      val episode = podcast.episodes.first()
      val savedEpisode = DatabaseFixture.savedEpisode(podcast = podcast, episode = episode)
      val queuedEpisode = DatabaseFixture.queuedEpisode(podcast = podcast, episode = episode)
      database.insert(podcast)
      saved.save(savedEpisode)

      database.insert(podcast.copy(episodes = podcast.episodes.drop(1)))
      assertThat(saved.savedEpisodes.first()).isEqualTo(listOf(savedEpisode))

      queue.enqueue(queuedEpisode)
      saved.removeSavedEpisode(feedUrl = savedEpisode.feedUrl, episodeId = episode.id)
      assertThat(saved.savedEpisodes.first()).isEmpty()
      assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(queuedEpisode))
    }
  }
}
