package software.ralf.storymile.database

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
        assertThat(database.podcasts.first()).isEmpty()

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

  @Test
  fun `restoring an episode reuses its position when free and appends when occupied`() = runTest {
    DatabaseFixture().use {
      val podcast = DatabaseFixture.podcast()
      val first =
        DatabaseFixture.queuedEpisode(podcast = podcast, episode = podcast.episodes.first())
      val second = first.copy(feedUrl = "https://feeds.test/second.xml")
      val removed = first.copy(feedUrl = "https://feeds.test/removed.xml")
      val last = first.copy(feedUrl = "https://feeds.test/last.xml")
      listOf(first, second, removed, last).forEach { queue.enqueue(it) }

      val position =
        checkNotNull(
          queue.removeFromQueue(feedUrl = removed.feedUrl, episodeId = removed.episode.id),
        )
      assertThat(queue.removeFromQueue(feedUrl = removed.feedUrl, episodeId = removed.episode.id))
        .isNull()
      val freePosition =
        checkNotNull(queue.removeFromQueue(feedUrl = second.feedUrl, episodeId = second.episode.id))

      queue.enqueue(episode = removed, preferredPosition = position)
      assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(first, removed, last))

      queue.removeFromQueue(feedUrl = removed.feedUrl, episodeId = removed.episode.id)
      queue.moveInQueue(
        feedUrl = first.feedUrl,
        episodeId = first.episode.id,
        targetFeedUrl = last.feedUrl,
        targetEpisodeId = last.episode.id,
      )
      queue.enqueue(episode = removed, preferredPosition = position)
      assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(last, first, removed))

      queue.enqueue(episode = removed, preferredPosition = freePosition)
      assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(last, first, removed))
    }
  }

  @Test
  fun `queued episodes share feed metadata and survive leaving the current feed`() = runTest {
    DatabaseFixture().use { database ->
      val podcast = DatabaseFixture.podcast()
      val episode = podcast.episodes.first()
      val original = DatabaseFixture.queuedEpisode(podcast = podcast, episode = episode)
      queue.enqueue(original)

      queue.queuedEpisodes.test {
        assertThat(awaitItem()).isEqualTo(listOf(original))

        val updatedEpisode =
          episode.copy(
            title = "Updated episode",
            enclosure = episode.enclosure.copy(url = "https://media.test/updated.mp3"),
          )
        val followed =
          podcast.copy(
            title = "Updated show",
            episodes = listOf(updatedEpisode) + podcast.episodes.drop(1),
          )
        database.insert(followed)
        val updated = DatabaseFixture.queuedEpisode(podcast = followed, episode = updatedEpisode)
        assertThat(awaitItem()).isEqualTo(listOf(updated))
        assertThat(database.podcasts.first()).isEqualTo(listOf(followed))

        val withoutQueuedEpisode = followed.copy(episodes = followed.episodes.drop(1))
        database.insert(withoutQueuedEpisode)
        assertThat(database.podcasts.first()).isEqualTo(listOf(withoutQueuedEpisode))
        assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(updated))

        database.delete(followed.feedUrl)
        assertThat(database.podcasts.first()).isEmpty()
        assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(updated))

        val returnedEpisode =
          updatedEpisode.copy(id = "updated-source-id", title = "Episode listed again")
        val followedAgain = followed.copy(episodes = listOf(returnedEpisode))
        database.insert(followedAgain)
        assertThat(awaitItem())
          .isEqualTo(
            listOf(
              DatabaseFixture.queuedEpisode(podcast = followedAgain, episode = returnedEpisode),
            ),
          )
        assertThat(database.podcasts.first()).isEqualTo(listOf(followedAgain))
      }
    }
  }

  @Test
  fun `a shared media URL does not transfer a queue entry to a new episode ID`() = runTest {
    DatabaseFixture().use { database ->
      val podcast = DatabaseFixture.podcast()
      val first = podcast.episodes.first()
      val second = first.copy(id = "second-id", title = "Another episode")
      val queued = DatabaseFixture.queuedEpisode(podcast = podcast, episode = first)
      database.insert(podcast.copy(episodes = listOf(first, second)))
      queue.enqueue(queued)

      val replacement = podcast.copy(episodes = listOf(first.copy(id = "new-id")))
      database.insert(replacement)

      assertThat(database.podcasts.first()).isEqualTo(listOf(replacement))
      assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(queued))
    }
  }

  @Test
  fun `a move uses both entry identities and shifts only the intervening episodes`() = runTest {
    DatabaseFixture().use {
      val podcast = DatabaseFixture.podcast()
      val first =
        DatabaseFixture.queuedEpisode(podcast = podcast, episode = podcast.episodes.first())
      val removed =
        DatabaseFixture.queuedEpisode(podcast = podcast, episode = podcast.episodes.last())
      val middle = first.copy(feedUrl = "https://feeds.test/middle.xml")
      val last = first.copy(feedUrl = "https://feeds.test/last.xml")
      listOf(first, removed, middle, last).forEach { queue.enqueue(it) }
      queue.removeFromQueue(feedUrl = removed.feedUrl, episodeId = removed.episode.id)

      queue.queuedEpisodes.test {
        assertThat(awaitItem()).isEqualTo(listOf(first, middle, last))

        queue.moveInQueue(
          feedUrl = last.feedUrl,
          episodeId = last.episode.id,
          targetFeedUrl = first.feedUrl,
          targetEpisodeId = first.episode.id,
        )
        assertThat(awaitItem()).isEqualTo(listOf(last, first, middle))

        queue.moveInQueue(
          feedUrl = last.feedUrl,
          episodeId = last.episode.id,
          targetFeedUrl = first.feedUrl,
          targetEpisodeId = first.episode.id,
        )
        assertThat(awaitItem()).isEqualTo(listOf(first, last, middle))

        queue.moveInQueue(
          feedUrl = first.feedUrl,
          episodeId = first.episode.id,
          targetFeedUrl = removed.feedUrl,
          targetEpisodeId = removed.episode.id,
        )
        assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(first, last, middle))

        queue.enqueue(removed)
        assertThat(awaitItem()).isEqualTo(listOf(first, last, middle, removed))
      }
    }
  }
}
