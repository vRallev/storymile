@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.coroutine.DefaultCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.IoCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.MainCoroutineDispatcher
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.coroutine.metro.CoroutineDispatcherGraph
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.restart.DesktopProcessRestarter
import software.ralf.storymile.restart.ProcessRestarter
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.storage.DesktopStoragePlatform
import software.ralf.storymile.storage.OkioStoragePlatform
import software.ralf.storymile.storage.ScopedStorage
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.storage.StoragePlatform
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

class RuntimeModeStorageTest {
  @Test
  fun `startup restores the selected mode without restarting and accepts an override`() = runTest {
    withStoragePlatform { platform, _ ->
      withApplication(platform, initialMode = RuntimeMode.Fake) { graph, _, restarter ->
        graph.awaitSavedMode(RuntimeMode.Fake)
        assertThat(graph.runtimeModeController.mode.value).isEqualTo(RuntimeMode.Fake)
        assertThat(restarter.calls).isEqualTo(0)
      }

      withApplication(platform) { graph, _, restarter ->
        graph.storage.assertContent(null, null)
        assertThat(graph.runtimeModeController.mode.value).isEqualTo(RuntimeMode.Fake)
        assertThat(restarter.calls).isEqualTo(0)
      }

      withApplication(platform, initialMode = RuntimeMode.Real) { graph, _, restarter ->
        graph.awaitSavedMode(RuntimeMode.Real)
        assertThat(graph.runtimeModeController.mode.value).isEqualTo(RuntimeMode.Real)
        assertThat(restarter.calls).isEqualTo(0)
      }
    }
  }

  @Test
  fun `mode changes restart app and guest scopes while keeping real data separate`() = runTest {
    withStoragePlatform { platform, root ->
      val realFiles =
        withApplication(platform, initialMode = RuntimeMode.Real) { graph, application, restarter ->
          val userScope = graph.userManager.userScope.value
          val userStorage = userScope.metroDependencyGraph<UserBindings>().storage
          graph.storage.writeContent("real-app", 1)
          userStorage.writeContent("real-guest", 2)
          graph.awaitSavedMode(RuntimeMode.Real)
          val savedFiles = root.featureFiles()
          val appPreferences = graph.storage.preferences("library")
          val userPreferences = userStorage.preferences("library")
          val userWork = userScope.launch { awaitCancellation() }

          graph.runtimeModeController.switchMode(RuntimeMode.Real)
          runCurrent()
          assertThat(restarter.calls).isEqualTo(0)
          assertThat(application.rootScope.isDestroyed()).isFalse()
          assertThat(userScope.isDestroyed()).isFalse()

          graph.runtimeModeController.switchMode(RuntimeMode.Fake)
          restarter.awaitRestart()

          assertThat(restarter.calls).isEqualTo(1)
          assertThat(application.rootScope.isDestroyed()).isTrue()
          assertThat(userScope.isDestroyed()).isTrue()
          assertThat(userWork.isCancelled).isTrue()
          assertFailure { graph.userManager.userScope }.isInstanceOf<IllegalStateException>()
          assertFailure { appPreferences.data.first() }.isInstanceOf<CancellationException>()
          assertFailure { graph.storage.files.read("book") }.isInstanceOf<CancellationException>()
          assertFailure { graph.storage.cache.read("cover") }.isInstanceOf<CancellationException>()
          assertFailure { userPreferences.data.first() }.isInstanceOf<CancellationException>()
          assertFailure { userStorage.files.read("book") }.isInstanceOf<CancellationException>()
          assertFailure { userStorage.cache.read("cover") }.isInstanceOf<CancellationException>()
          savedFiles
        }

      withApplication(platform) { graph, _, restarter ->
        val userStorage =
          graph.userManager.userScope.value.metroDependencyGraph<UserBindings>().storage
        assertThat(graph.storage.files.read("book")).isNull()
        assertThat(userStorage.cache.read("cover")).isNull()
        graph.storage.assertContent(null, null)
        userStorage.assertContent(null, null)
        assertThat(graph.runtimeModeController.mode.value).isEqualTo(RuntimeMode.Fake)
        graph.storage.writeContent("fake-app", 3)
        userStorage.writeContent("fake-guest", 4)
        assertThat(root.featureFiles()).isEqualTo(realFiles)

        graph.runtimeModeController.switchMode(RuntimeMode.Real)
        restarter.awaitRestart()
        assertThat(restarter.calls).isEqualTo(1)
      }

      withApplication(platform) { graph, _, restarter ->
        val userStorage =
          graph.userManager.userScope.value.metroDependencyGraph<UserBindings>().storage
        graph.storage.assertContent("real-app", 1)
        userStorage.assertContent("real-guest", 2)
        assertThat(graph.runtimeModeController.mode.value).isEqualTo(RuntimeMode.Real)

        graph.runtimeModeController.switchMode(RuntimeMode.Fake)
        restarter.awaitRestart()
      }

      withApplication(platform) { graph, _, restarter ->
        val userStorage =
          graph.userManager.userScope.value.metroDependencyGraph<UserBindings>().storage
        graph.storage.assertContent(null, null)
        userStorage.assertContent(null, null)
        assertThat(graph.runtimeModeController.mode.value).isEqualTo(RuntimeMode.Fake)
        assertThat(root.featureFiles()).isEqualTo(realFiles)
        assertThat(restarter.calls).isEqualTo(0)
      }
    }
  }

  private suspend fun Storage.writeContent(value: String, byte: Int) {
    preferences("library").edit { it[stringPreferencesKey("book")] = value }
    files.write("book", byteArrayOf(byte.toByte()))
    cache.write("cover", byteArrayOf(byte.toByte()))
  }

  private suspend fun Storage.assertContent(value: String?, byte: Int?) {
    assertThat(preferences("library").data.first()[stringPreferencesKey("book")]).isEqualTo(value)
    if (byte == null) {
      assertThat(files.read("book")).isNull()
      assertThat(cache.read("cover")).isNull()
    } else {
      assertThat(files.read("book")).isNotNull().isEqualTo(byteArrayOf(byte.toByte()))
      assertThat(cache.read("cover")).isNotNull().isEqualTo(byteArrayOf(byte.toByte()))
    }
  }

  private suspend fun Graph.awaitSavedMode(mode: RuntimeMode) {
    val key = stringPreferencesKey("mode")
    val saved = modeStorage.preferences("runtime-mode").data.first { it[key] == mode.name }
    assertThat(saved[key]).isEqualTo(mode.name)
  }

  private fun Path.featureFiles(): Map<Path, ByteString> {
    val modeNamespace = "runtime-mode".encodeUtf8().hex()
    return FileSystem.SYSTEM.listRecursively(this)
      .filter {
        FileSystem.SYSTEM.metadata(it).isRegularFile &&
          it.relativeTo(this).segments.first() != modeNamespace
      }
      .associateWith { FileSystem.SYSTEM.read(it) { readByteString() } }
  }

  private suspend fun TestScope.withStoragePlatform(
    block: suspend (StoragePlatform, Path) -> Unit,
  ) {
    val root = Files.createTempDirectory("storymile-runtime-mode").toString().toPath()
    val platform =
      object : OkioStoragePlatform(FileSystem.SYSTEM, StandardTestDispatcher(testScheduler)) {
        override fun rootDirectory(): Path = root

        override fun cacheDirectory(): Path = root / "cache"
      }
    try {
      block(platform, root)
    } finally {
      FileSystem.SYSTEM.deleteRecursively(root)
    }
  }

  private suspend fun <T> TestScope.withApplication(
    platform: StoragePlatform,
    initialMode: RuntimeMode? = null,
    block: suspend (Graph, KmpApplication, RecordingProcessRestarter) -> T,
  ): T {
    val application = KmpApplication()
    val dispatcher = StandardTestDispatcher(testScheduler)
    val restarter = RecordingProcessRestarter()
    val graph =
      createGraphFactory<Graph.Factory>()
        .create(application, platform, restarter, dispatcher, dispatcher, dispatcher)
    if (initialMode != null) {
      graph.runtimeModeController.setInitialMode(initialMode)
    }
    application.create(graph)
    try {
      return block(graph, application, restarter)
    } finally {
      if (!application.rootScope.isDestroyed()) {
        application.destroy()
      }
      runCurrent()
    }
  }

  @DependencyGraph(
    AppScope::class,
    excludes =
      [
        DesktopStoragePlatform::class,
        DesktopProcessRestarter::class,
        CoroutineDispatcherGraph::class,
      ],
  )
  interface Graph : AppGraph {
    @ForScope(RuntimeModeController::class) val modeStorage: ScopedStorage

    val userManager: UserManager

    @DependencyGraph.Factory
    fun interface Factory {
      fun create(
        @Provides rootScopeProvider: RootScopeProvider,
        @Provides platform: StoragePlatform,
        @Provides processRestarter: ProcessRestarter,
        @Provides @IoCoroutineDispatcher ioDispatcher: CoroutineDispatcher,
        @Provides @DefaultCoroutineDispatcher defaultDispatcher: CoroutineDispatcher,
        @Provides @MainCoroutineDispatcher mainDispatcher: CoroutineDispatcher,
      ): Graph
    }
  }

  @ContributesTo(UserScope::class)
  interface UserBindings {
    @ForScope(UserScope::class) val storage: Storage
  }

  private class RecordingProcessRestarter : ProcessRestarter {
    var calls = 0
      private set

    private val restarted = CompletableDeferred<Unit>()

    override fun restart() {
      calls += 1
      restarted.complete(Unit)
    }

    suspend fun awaitRestart() {
      restarted.await()
    }
  }
}
