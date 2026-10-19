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
      try {
        DatabaseFixture(path).use { database -> database.insert(podcast) }
        DatabaseFixture().use { memory ->
          assertThat(memory.podcasts.first()).isEmpty()
          memory.insert(podcast.copy(title = "Fake mode"))
        }
        DatabaseFixture((directory / "other-user.db").toString()).use { other ->
          assertThat(other.podcasts.first()).isEmpty()
        }
        DatabaseFixture(path).use { reopened ->
          assertThat(reopened.podcasts.first()).isEqualTo(listOf(podcast))
        }
      } finally {
        FileSystem.SYSTEM.deleteRecursively(directory)
      }
    }
}
