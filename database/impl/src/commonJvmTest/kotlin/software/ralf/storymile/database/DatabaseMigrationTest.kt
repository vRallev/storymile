package software.ralf.storymile.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.podcast.SavedEpisode

class DatabaseMigrationTest {
  private val followed = DatabaseFixture.podcast()
  private val queueOnlyEpisode =
    QueuedEpisode(
      feedUrl = "https://feeds.test/queue.xml",
      podcastTitle = "Queue-only show",
      artworkUrl = "https://images.test/queue.png",
      episode = followed.episodes.last(),
    )
  private val queuedEpisodes =
    listOf(
      queueOnlyEpisode,
      DatabaseFixture.queuedEpisode(podcast = followed, episode = followed.episodes.first()),
    )
  private val savedEpisodes =
    listOf(
      SavedEpisode(
        feedUrl = queueOnlyEpisode.feedUrl,
        podcastTitle = queueOnlyEpisode.podcastTitle,
        artworkUrl = queueOnlyEpisode.artworkUrl,
        episode = queueOnlyEpisode.episode,
      ),
    )

  @Test
  fun `migrates followed shows from the first schema`() = runTest {
    assertMigration(fixture = "followed")
  }

  @Test
  fun `migrates followed shows and queue entries keyed by feed URL`() = runTest {
    val artwork = "https://images.test/queue-episode.png"
    val episodeWithArtwork =
      queueOnlyEpisode.copy(
        artworkUrl = artwork,
        episode =
          queueOnlyEpisode.episode.copy(
            id = "covered",
            title = "Episode with artwork",
            artworkUrl = artwork,
            enclosure =
              queueOnlyEpisode.episode.enclosure.copy(url = "https://media.test/covered.mp3"),
          ),
      )
    assertMigration(
      fixture = "feed-url-queue",
      expectedQueue = listOf(episodeWithArtwork) + queuedEpisodes,
    )
  }

  @Test
  fun `adds the saved episodes table to databases with local IDs`() = runTest {
    assertMigration(fixture = "local-id-queue", expectedQueue = queuedEpisodes)
  }

  @Test
  fun `migrates saved episodes from before the episode cleanup index`() = runTest {
    assertMigration(
      fixture = "saved",
      expectedQueue = queuedEpisodes,
      expectedSaved = savedEpisodes,
    )
  }

  @Test
  fun `migrates saved episodes when the episode cleanup index already exists`() = runTest {
    assertMigration(
      fixture = "cleanup-index",
      expectedQueue = queuedEpisodes,
      expectedSaved = savedEpisodes,
    )
  }

  private suspend fun assertMigration(
    fixture: String,
    expectedQueue: List<QueuedEpisode> = emptyList(),
    expectedSaved: List<SavedEpisode> = emptyList(),
  ) {
    val schema = checkNotNull(javaClass.getResource("/podcast-database/$fixture.sql")).readText()
    val directory = Files.createTempDirectory("storymile-migrate-").toString().toPath()
    val path = (directory / "podcasts.db").toString()
    try {
      BundledSQLiteDriver().open(path).use { connection ->
        schema
          .lineSequence()
          .filter { it.isNotBlank() && !it.startsWith("--") }
          .forEach {
            connection.execSQL(it)
          }
      }
      repeat(2) {
        DatabaseFixture(path).use { database ->
          assertThat(database.podcasts.first()).isEqualTo(listOf(followed))
          assertThat(queue.queuedEpisodes.first()).isEqualTo(expectedQueue)
          assertThat(saved.savedEpisodes.first()).isEqualTo(expectedSaved)
        }
      }
    } finally {
      FileSystem.SYSTEM.deleteRecursively(directory)
    }
  }
}
