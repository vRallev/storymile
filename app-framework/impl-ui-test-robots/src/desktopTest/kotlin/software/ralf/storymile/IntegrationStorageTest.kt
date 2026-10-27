package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
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
        assertThat(first.files.read("integration-test")).isNotNull().isEqualTo(byteArrayOf(1))
        assertThat(first.cache.read("integration-test")).isNotNull().isEqualTo(byteArrayOf(2))
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

    assertFailure { storage.files.read("integration-test") }.isInstanceOf<CancellationException>()
    assertFailure { storage.cache.read("integration-test") }.isInstanceOf<CancellationException>()
    assertFailure { storage.preferences("integration-test") }.isInstanceOf<CancellationException>()
    assertFailure { preferences.data.first() }.isInstanceOf<CancellationException>()
    assertFailure { preferences.updateData { it } }.isInstanceOf<CancellationException>()
  }

  private suspend fun assertEmpty(storage: Storage) {
    assertThat(storage.preferences("integration-test").data.first().asMap()).isEqualTo(emptyMap())
    assertThat(storage.files.read("integration-test")).isNull()
    assertThat(storage.cache.read("integration-test")).isNull()
  }

  private suspend fun <T> withStorage(block: suspend (Storage) -> T): T {
    val application = KmpApplication {
      createGraphFactory<TestDesktopAppGraph.Factory>().create(it)
    }
    try {
      application.awaitReady()
      return block(application.rootScope.metroDependencyGraph<AppGraph>().storage)
    } finally {
      application.destroy()
    }
  }
}
