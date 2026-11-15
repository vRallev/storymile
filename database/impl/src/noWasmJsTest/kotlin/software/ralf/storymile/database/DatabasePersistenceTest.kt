package software.ralf.storymile.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
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
      val directory =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "storymile-database-${Random.nextLong().toULong()}"
      FileSystem.SYSTEM.createDirectories(directory)
      val path = (directory / "podcasts.db").toString()
      val podcast = DatabaseFixture.podcast()
      val queued =
        podcast.episodes.map { DatabaseFixture.queuedEpisode(podcast = podcast, episode = it) }
      try {
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
      } finally {
        FileSystem.SYSTEM.deleteRecursively(directory)
      }
    }

  @Test
  fun `existing followed shows and episodes survive the addition of the queue`() =
    withDatabaseDirectory { directory ->
      val path = (directory / "podcasts.db").toString()
      createVersionOneDatabase(path)

      DatabaseFixture(path).use { database ->
        val saved = database.podcasts.first().single()
        assertThat(saved.feedUrl).isEqualTo("https://feeds.test/old.xml")
        assertThat(saved.title).isEqualTo("Saved show")
        assertThat(saved.episodes.single().title).isEqualTo("Saved episode")
        assertThat(queue.queuedEpisodes.first()).isEmpty()

        val podcast = DatabaseFixture.podcast()
        database.insert(podcast)
        assertThat(database.podcasts.first().map { it.feedUrl }.toSet())
          .isEqualTo(setOf(saved.feedUrl, podcast.feedUrl))
        val queued =
          DatabaseFixture.queuedEpisode(podcast = podcast, episode = podcast.episodes.first())
        queue.enqueue(queued)
        assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(queued))
      }
    }

  @Test
  fun `episodes queued by the version 1 development build survive the upgrade`() =
    withDatabaseDirectory { directory ->
      val path = (directory / "podcasts.db").toString()
      createVersionOneDatabase(path = path, withQueue = true)

      DatabaseFixture(path).use {
        val queued = queue.queuedEpisodes.first().single()
        assertThat(queued.feedUrl).isEqualTo("https://feeds.test/old.xml")
        assertThat(queued.podcastTitle).isEqualTo("Saved show")
        assertThat(queued.episode.title).isEqualTo("Saved episode")
        assertThat(queued.episode.enclosure.url).isEqualTo("https://media.test/old.mp3")
      }
    }

  private fun createVersionOneDatabase(path: String, withQueue: Boolean = false) {
    BundledSQLiteDriver().open(path).use { connection ->
      connection.execSQL(
        """
        CREATE TABLE shows (
          feedUrl TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, description TEXT, author TEXT,
          artworkUrl TEXT, link TEXT
        )
        """
          .trimIndent(),
      )
      connection.execSQL(
        """
        CREATE TABLE episodes (
          feedUrl TEXT NOT NULL, id TEXT NOT NULL, position INTEGER NOT NULL, title TEXT NOT NULL,
          description TEXT, author TEXT, artworkUrl TEXT, publishedAt TEXT, durationMillis INTEGER,
          url TEXT NOT NULL, type TEXT, length INTEGER, PRIMARY KEY (feedUrl, id),
          FOREIGN KEY (feedUrl) REFERENCES shows (feedUrl) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """
          .trimIndent(),
      )
      connection.execSQL("CREATE INDEX index_episodes_feedUrl ON episodes (feedUrl)")
      connection.execSQL(
        "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
      )
      val identityHash =
        if (withQueue) "c99c4292151c06c29e0a819cf01ce036" else "af8d1ed076aeac6ed69c02cc6f837b26"
      connection.execSQL("INSERT INTO room_master_table VALUES (42, '$identityHash')")
      connection.execSQL("PRAGMA user_version = 1")
      connection.execSQL(
        "INSERT INTO shows (feedUrl, title) VALUES ('https://feeds.test/old.xml', 'Saved show')",
      )
      connection.execSQL(
        """
        INSERT INTO episodes (feedUrl, id, position, title, url)
        VALUES ('https://feeds.test/old.xml', 'old', 0, 'Saved episode', 'https://media.test/old.mp3')
        """
          .trimIndent(),
      )
      if (withQueue) {
        connection.execSQL(
          """
          CREATE TABLE queued_episodes (
            position INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, feedUrl TEXT NOT NULL,
            podcastTitle TEXT NOT NULL, artworkUrl TEXT, episodeId TEXT NOT NULL,
            title TEXT NOT NULL, description TEXT, author TEXT, episodeArtworkUrl TEXT,
            publishedAt TEXT, durationMillis INTEGER, url TEXT NOT NULL, type TEXT, length INTEGER
          )
          """
            .trimIndent(),
        )
        connection.execSQL(
          """
          CREATE UNIQUE INDEX index_queued_episodes_feedUrl_episodeId
          ON queued_episodes (feedUrl, episodeId)
          """
            .trimIndent(),
        )
        connection.execSQL(
          """
          INSERT INTO queued_episodes (feedUrl, podcastTitle, episodeId, title, url)
          SELECT shows.feedUrl, shows.title, episodes.id, episodes.title, episodes.url
          FROM shows JOIN episodes ON shows.feedUrl = episodes.feedUrl
          """
            .trimIndent(),
        )
      }
    }
  }

  private fun withDatabaseDirectory(block: suspend (Path) -> Unit) = runTest {
    val directory =
      FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "storymile-database-${Random.nextLong().toULong()}"
    FileSystem.SYSTEM.createDirectories(directory)
    try {
      block(directory)
    } finally {
      FileSystem.SYSTEM.deleteRecursively(directory)
    }
  }
}
