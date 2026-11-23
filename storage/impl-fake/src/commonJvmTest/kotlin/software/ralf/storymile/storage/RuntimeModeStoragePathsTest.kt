package software.ralf.storymile.storage

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNull
import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.test.StandardTestDispatcher
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

class RuntimeModeStoragePathsTest {
  @Test
  fun `fake mode returns no directory paths on first access`() = runTestWithScope { scope ->
    val root = Files.createTempDirectory("storymile-storage").toString().toPath()
    try {
      val platform =
        object :
          OkioStoragePlatform(
            fileSystem = FileSystem.SYSTEM,
            ioDispatcher = StandardTestDispatcher(testScheduler),
          ) {
          override fun rootDirectory(): Path = root

          override fun cacheDirectory(): Path = root / "cache"
        }
      val factory: Storage.Factory =
        RuntimeModeStorageFactory(
          real = DefaultStorage.Factory(platform),
          controller = lazy { FakeRuntimeModeController(RuntimeMode.Fake) },
        )
      val storage = factory.create("app", scope.coroutineScope())

      assertThat(storage.files.path).isNull()
      assertThat(storage.cache.directory("coil").path).isNull()
      assertThat(FileSystem.SYSTEM.list(root)).isEmpty()
    } finally {
      scope.destroy()
      FileSystem.SYSTEM.deleteRecursively(root)
    }
  }
}
