package software.ralf.storymile.database

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class BrowserDatabasePersistenceTest {
  @Test
  fun `podcasts survive closing and reopening browser storage`() = runTest {
    val podcast = DatabaseFixture.podcast()
    val path = "/database-persistence-test.db"
    DatabaseFixture(path).use { database -> database.insert(podcast) }

    DatabaseFixture(path).use { database ->
      assertThat(database.podcasts.first()).isEqualTo(listOf(podcast))
    }
    DatabaseFixture().use { database -> assertThat(database.podcasts.first()).isEmpty() }
  }
}
