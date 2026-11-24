package software.ralf.storymile.database

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class BrowserDatabasePersistenceTest {
  @Test
  fun `deleting a retired namespace leaves the active database usable`() = runTest {
    val files = BrowserStorageDatabasesImpl()
    val retired = "6161"
    val active = "616161"
    val retiredPath = "/storymile-$retired-706f646361737473.db"
    val activePath = "/storymile-$active-706f646361737473.db"
    val podcast = DatabaseFixture.podcast()
    DatabaseFixture(retiredPath).use { it.insert(podcast) }
    try {
      DatabaseFixture(activePath).use { database ->
        database.insert(podcast)
        assertThat(files.hasDatabases(retired)).isTrue()
        files.deleteDatabases(retired)
        assertThat(files.hasDatabases(retired)).isFalse()
        assertThat(database.podcasts.first()).isEqualTo(listOf(podcast))
      }
      DatabaseFixture(retiredPath).use { assertThat(it.podcasts.first()).isEmpty() }
    } finally {
      files.deleteDatabases(retired)
      files.deleteDatabases(active)
    }
  }

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
