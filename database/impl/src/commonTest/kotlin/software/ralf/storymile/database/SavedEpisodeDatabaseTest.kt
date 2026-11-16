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
      val third = first.copy(feedUrl = "https://feeds.test/third.xml")

      saved.savedEpisodes.test {
        assertThat(awaitItem()).isEmpty()

        saved.save(first)
        assertThat(awaitItem()).isEqualTo(listOf(first))

        saved.save(second)
        assertThat(awaitItem()).isEqualTo(listOf(second, first))

        saved.save(third)
        assertThat(awaitItem()).isEqualTo(listOf(third, second, first))
        assertThat(database.podcasts.first()).isEmpty()

        val previousId =
          checkNotNull(
            saved.removeSavedEpisode(feedUrl = second.feedUrl, episodeId = second.episode.id),
          )
        assertThat(awaitItem()).isEqualTo(listOf(third, first))

        saved.save(episode = second, previousId = previousId)
        assertThat(awaitItem()).isEqualTo(listOf(third, second, first))

        saved.removeSavedEpisode(feedUrl = second.feedUrl, episodeId = second.episode.id)
        assertThat(awaitItem()).isEqualTo(listOf(third, first))

        saved.save(second)
        assertThat(awaitItem()).isEqualTo(listOf(second, third, first))

        saved.save(episode = second, previousId = previousId)
        assertThat(saved.savedEpisodes.first()).isEqualTo(listOf(second, third, first))
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
