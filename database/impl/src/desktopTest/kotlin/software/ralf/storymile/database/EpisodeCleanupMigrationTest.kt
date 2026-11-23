package software.ralf.storymile.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class EpisodeCleanupMigrationTest {
  @Test
  fun `version one retains library entries and clears obsolete metadata on removal`() = runTest {
    val directory = createTempDirectory("storymile-migration")
    val path = directory.resolve("podcasts.db")
    try {
      MigrationTestHelper(
          schemaDirectoryPath = Path("schemas"),
          databasePath = path,
          driver = BundledSQLiteDriver(),
          databaseClass = RoomPodcastDatabase::class,
          databaseFactory = { RoomPodcastDatabaseConstructor.initialize() },
        )
        .createDatabase(version = 1)
        .use { connection ->
          connection.execSQL(
            """INSERT INTO shows (id, feedUrl, title, isFollowed)
               VALUES (1, 'followed', 'Followed', 1), (2, 'unfollowed', 'Old title', 0)""",
          )
          connection.execSQL(
            """INSERT INTO episodes (id, showId, sourceEpisodeId, feedPosition, title, url) VALUES
               (1, 1, 'older', 1, 'Older', 'older.mp3'),
               (2, 1, 'newer', 0, 'Newer', 'newer.mp3'),
               (3, 2, 'queued', NULL, 'Queued', 'queued.mp3'),
               (4, 2, 'orphan', NULL, 'Stale episode', 'stale.mp3'),
               (100, 2, 'deleted', NULL, 'Deleted', 'deleted.mp3')""",
          )
          connection.execSQL("DELETE FROM episodes WHERE id = 100")
          connection.execSQL("INSERT INTO queued_episodes (episodeId, position) VALUES (3, 10)")
          connection.execSQL("INSERT INTO saved_episodes (id, episodeId) VALUES (8, 1)")
        }

      DatabaseFixture(path.toString()).use { database ->
        val followed = database.podcasts.first().single()
        assertThat(followed.title).isEqualTo("Followed")
        assertThat(followed.episodes.map { it.id }).isEqualTo(listOf("newer", "older"))
        assertThat(saved.savedEpisodes.first().single().episode).isEqualTo(followed.episodes.last())

        val queued = queue.queuedEpisodes.first().single()
        assertThat(queued.episode.title).isEqualTo("Queued")
        assertThat(queue.removeFromQueue(feedUrl = queued.feedUrl, episodeId = queued.episode.id))
          .isEqualTo(10L)
        val fresh =
          queued.copy(
            podcastTitle = "New title",
            episode = queued.episode.copy(id = "orphan", title = "Fresh episode"),
          )
        queue.enqueue(fresh)
        assertThat(queue.queuedEpisodes.first()).isEqualTo(listOf(fresh))
      }
      BundledSQLiteDriver().open(path.toString()).use { connection ->
        connection.prepare("SELECT seq FROM sqlite_sequence WHERE name = 'episodes'").use { query ->
          assertThat(query.step()).isTrue()
          assertThat(query.getLong(0)).isEqualTo(101L)
        }
      }
    } finally {
      directory.toFile().deleteRecursively()
    }
  }
}
