package software.ralf.storymile.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path

class DatabasePersistenceTest {
  @Test
  fun `disk data survives reopening and stays separate from other users and memory databases`() =
    runTest {
      withDatabaseDirectory { directory ->
        val path = (directory / "podcasts.db").toString()
        val podcast = DatabaseFixture.podcast()
        val queued =
          podcast.episodes.map { DatabaseFixture.queuedEpisode(podcast = podcast, episode = it) }
        DatabaseFixture(path).use { database ->
          database.insert(podcast)
          queued.forEach { queue.enqueue(it) }
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
          assertThat(queue.queuedEpisodes.first()).isEqualTo(queued)
          reopened.delete(podcast.feedUrl)
        }
        DatabaseFixture(path).use { reopened ->
          assertThat(reopened.podcasts.first()).isEmpty()
        }
      }
    }

  @Test
  fun `upgrading a version one database preserves follows and initializes the queue`() = runTest {
    withDatabaseDirectory { directory ->
      val path = (directory / "podcasts.db").toString()
      createVersionOneDatabase(path)

      DatabaseFixture(path).use { database ->
        val expected =
          DatabaseFixture.podcast()
            .copy(description = null, author = null, artworkUrl = null, link = null)
            .let { it.copy(episodes = it.episodes.takeLast(1)) }
        assertThat(database.podcasts.first()).isEqualTo(listOf(expected))
        assertThat(queue.queuedEpisodes.first()).isEmpty()

        val episode =
          DatabaseFixture.queuedEpisode(podcast = expected, episode = expected.episodes.single())
        queue.enqueue(episode)
        assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(episode))
      }
    }
  }

  private suspend fun withDatabaseDirectory(block: suspend (Path) -> Unit) {
    val directory =
      FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "storymile-database-${Random.nextLong().toULong()}"
    FileSystem.SYSTEM.createDirectories(directory)
    try {
      block(directory)
    } finally {
      FileSystem.SYSTEM.deleteRecursively(directory)
    }
  }

  private fun createVersionOneDatabase(path: String) {
    val statements =
      listOf(
        """CREATE TABLE shows (feedUrl TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL,
          description TEXT, author TEXT, artworkUrl TEXT, link TEXT)""",
        """CREATE TABLE episodes (feedUrl TEXT NOT NULL, id TEXT NOT NULL, position INTEGER NOT NULL,
          title TEXT NOT NULL, description TEXT, author TEXT, artworkUrl TEXT, publishedAt TEXT,
          durationMillis INTEGER, url TEXT NOT NULL, type TEXT, length INTEGER,
          PRIMARY KEY(feedUrl, id), FOREIGN KEY(feedUrl) REFERENCES shows(feedUrl) ON DELETE CASCADE)""",
        "CREATE INDEX index_episodes_feedUrl ON episodes (feedUrl)",
        "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
        "INSERT INTO room_master_table VALUES (42, 'af8d1ed076aeac6ed69c02cc6f837b26')",
        "INSERT INTO shows (feedUrl, title) VALUES ('https://feeds.test/show.xml', 'A show')",
        """INSERT INTO episodes (feedUrl, id, position, title, url)
          VALUES ('https://feeds.test/show.xml', 'first', 0, 'First episode', 'https://media.test/first.mp3')""",
        "PRAGMA user_version = 1",
      )
    BundledSQLiteDriver().open(path).use { connection ->
      statements.forEach { sql -> connection.prepare(sql).use { it.step() } }
    }
  }
}
