@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile

import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.coroutine.DefaultCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.IoCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.metro.CoroutineDispatcherGraph
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.storage.DesktopStoragePlatform
import software.ralf.storymile.storage.OkioStoragePlatform
import software.ralf.storymile.storage.StoragePlatform

class RuntimeModeStorageTest {
  @Test
  fun `app storage restores runtime mode unless startup selects another mode`() = runTest {
    val root = Files.createTempDirectory("storymile-runtime-mode").toString().toPath()
    val platform =
      object : OkioStoragePlatform(FileSystem.SYSTEM, StandardTestDispatcher(testScheduler)) {
        override fun rootDirectory(): Path = root

        override fun cacheDirectory(): Path = root / "cache"
      }
    val key = stringPreferencesKey("mode")

    try {
      withApplication(platform) { graph ->
        graph.runtimeModeController.switchMode(RuntimeMode.Fake)

        val saved = graph.storage.preferences("runtime-mode").data.first { it[key] == "Fake" }
        assertThat(saved[key]).isEqualTo("Fake")
      }

      withApplication(platform) { graph ->
        val restored = graph.runtimeModeController.mode.first { it == RuntimeMode.Fake }
        assertThat(restored).isEqualTo(RuntimeMode.Fake)
      }

      withApplication(platform, initialMode = RuntimeMode.Real) { graph ->
        val saved = graph.storage.preferences("runtime-mode").data.first { it[key] == "Real" }
        assertThat(saved[key]).isEqualTo("Real")
        assertThat(graph.runtimeModeController.mode.value).isEqualTo(RuntimeMode.Real)
      }
    } finally {
      FileSystem.SYSTEM.deleteRecursively(root)
    }
  }

  private suspend fun TestScope.withApplication(
    platform: StoragePlatform,
    initialMode: RuntimeMode? = null,
    block: suspend (AppGraph) -> Unit,
  ) {
    val application = KmpApplication()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val graph =
      createGraphFactory<Graph.Factory>()
        .create(application, platform, dispatcher, dispatcher, dispatcher)
    if (initialMode != null) {
      graph.runtimeModeController.switchMode(initialMode)
    }
    application.create(graph)
    try {
      block(graph)
    } finally {
      application.destroy()
      runCurrent()
    }
  }

  @DependencyGraph(
    AppScope::class,
    excludes = [DesktopStoragePlatform::class, CoroutineDispatcherGraph::class],
  )
  interface Graph : AppGraph {
    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides rootScopeProvider: RootScopeProvider,
        @Provides platform: StoragePlatform,
        @Provides @IoCoroutineDispatcher ioDispatcher: CoroutineDispatcher,
        @Provides @DefaultCoroutineDispatcher defaultDispatcher: CoroutineDispatcher,
        @Provides @MainCoroutineDispatcher mainDispatcher: CoroutineDispatcher,
      ): Graph
    }
  }
}
