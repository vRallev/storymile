package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.coroutine.destroyAndWait
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.restart.ApplicationRestarter
import software.ralf.storymile.restart.DefaultApplicationRestarter
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.storage.DesktopStoragePlatform
import software.ralf.storymile.storage.OkioStoragePlatform
import software.ralf.storymile.storage.PersistentStorage
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.storage.StoragePlatform
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class RuntimeModeStorageIntegrationTest {
  private val key = stringPreferencesKey("value")

  @Test
  fun `saved fake mode isolates app and guest data across application restarts`() = runTest {
    val directory = Files.createTempDirectory("storymile-runtime-storage").toOkioPath()
    val platform =
      object : OkioStoragePlatform(FileSystem.SYSTEM, Dispatchers.IO) {
        override fun rootDirectory() = directory / "files"

        override fun cacheDirectory() = directory / "cache"
      }
    try {
      withApplication(platform) { graph ->
        assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Real)
        graph.storages().forEachIndexed { index, storage -> write(storage, "real-$index") }
        graph.runtimeModeController.switchMode(RuntimeMode.Fake)
        assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Real)
      }

      withApplication(platform) { graph ->
        assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Fake)
        graph.storages().forEachIndexed { index, storage ->
          assertEmpty(storage)
          write(storage, "fake-$index")
        }
      }

      withApplication(platform) { graph ->
        assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Fake)
        graph.storages().forEach { assertEmpty(it) }
        graph.runtimeModeController.switchMode(RuntimeMode.Real)
        assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Fake)
      }

      withApplication(platform) { graph ->
        assertThat(graph.runtimeModeController.mode()).isEqualTo(RuntimeMode.Real)
        graph.storages().forEachIndexed { index, storage ->
          assertContents(storage, "real-$index")
        }
      }
    } finally {
      FileSystem.SYSTEM.deleteRecursively(directory)
    }
  }

  private fun Graph.storages(): List<Storage> =
    listOf(storage, userManager.userScope.value.metroDependencyGraph<UserStorageGraph>().storage)

  private suspend fun write(storage: Storage, value: String) {
    storage.preferences("settings").edit { it[key] = value }
    storage.files.write("value", value.encodeToByteArray())
    storage.cache.write("value", value.encodeToByteArray())
  }

  private suspend fun assertContents(storage: Storage, value: String) {
    assertThat(storage.preferences("settings").data.first()[key]).isEqualTo(value)
    assertThat(storage.files.read("value")).isNotNull().isEqualTo(value.encodeToByteArray())
    assertThat(storage.cache.read("value")).isNotNull().isEqualTo(value.encodeToByteArray())
  }

  private suspend fun assertEmpty(storage: Storage) {
    assertThat(storage.preferences("settings").data.first().asMap()).isEqualTo(emptyMap())
    assertThat(storage.files.read("value")).isNull()
    assertThat(storage.cache.read("value")).isNull()
  }

  private suspend fun withApplication(platform: StoragePlatform, block: suspend (Graph) -> Unit) {
    val application = KmpApplication()
    val graph =
      createGraphFactory<Graph.Factory>()
        .create(
          application,
          platform,
          object : ApplicationRestarter {
            override fun restart() = Unit
          },
        )
    application.create(graph)
    try {
      block(graph)
    } finally {
      application.rootScope.destroyAndWait()
      application.destroy()
    }
  }

  @DependencyGraph(
    AppScope::class,
    excludes =
      [
        DesktopStoragePlatform::class,
        DefaultApplicationRestarter::class,
        TestStorageBindings::class,
        TestRuntimeModeBindings::class,
      ],
  )
  interface Graph : AppGraph {
    val runtimeModeController: RuntimeModeController

    val userManager: UserManager

    @DependencyGraph.Factory
    interface Factory {
      fun create(
        @Provides rootScopeProvider: RootScopeProvider,
        @Provides @PersistentStorage platform: StoragePlatform,
        @Provides restarter: ApplicationRestarter,
      ): Graph
    }
  }

  @ContributesTo(UserScope::class)
  interface UserStorageGraph {
    @ForScope(UserScope::class) val storage: Storage
  }
}
