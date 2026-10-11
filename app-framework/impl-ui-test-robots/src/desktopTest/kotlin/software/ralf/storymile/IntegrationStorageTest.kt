package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.storage.Storage

class IntegrationStorageTest {
  @Test
  fun `test applications keep preferences files and caches isolated`() = runTest {
    val key = stringPreferencesKey("theme")
    withStorage { first ->
      first.preferences("integration-test").edit { it[key] = "dark" }
      first.files.write("integration-test", byteArrayOf(1))
      first.cache.write("integration-test", byteArrayOf(2))

      withStorage { second ->
        assertEmpty(second)
        second.preferences("integration-test").edit { it[key] = "light" }
        second.files.write("integration-test", byteArrayOf(3))
        second.cache.write("integration-test", byteArrayOf(4))

        assertThat(first.preferences("integration-test").data.first()[key]).isEqualTo("dark")
        assertContentEquals(byteArrayOf(1), first.files.read("integration-test"))
        assertContentEquals(byteArrayOf(2), first.cache.read("integration-test"))
      }
    }

    withStorage { assertEmpty(it) }
  }

  @Test
  fun `test application teardown closes injected storage handles`() = runTest {
    val (storage, preferences) =
      withStorage { storage ->
        storage.files.write("integration-test", byteArrayOf(1))
        storage.cache.write("integration-test", byteArrayOf(2))
        storage to storage.preferences("integration-test")
      }

    assertFailsWith<CancellationException> { storage.files.read("integration-test") }
    assertFailsWith<CancellationException> { storage.cache.read("integration-test") }
    assertFailsWith<CancellationException> { storage.preferences("integration-test") }
    assertFailsWith<CancellationException> { preferences.data.first() }
    assertFailsWith<CancellationException> { preferences.updateData { it } }
  }

  private suspend fun assertEmpty(storage: Storage) {
    assertThat(storage.preferences("integration-test").data.first().asMap()).isEqualTo(emptyMap())
    assertNull(storage.files.read("integration-test"))
    assertNull(storage.cache.read("integration-test"))
  }

  private suspend fun <T> withStorage(block: suspend (Storage) -> T): T {
    val application = KmpApplication()
    application.create(createGraphFactory<TestDesktopAppGraph.Factory>().create(application))
    try {
      return block(application.rootScope.metroDependencyGraph<AppGraph>().storage)
    } finally {
      application.destroy()
    }
  }
}
