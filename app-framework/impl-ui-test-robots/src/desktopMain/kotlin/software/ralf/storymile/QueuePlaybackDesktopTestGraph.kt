package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.TestRuntimeModeController
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController
import software.ralf.storymile.storage.DefaultStorage

@DependencyGraph(
  AppScope::class,
  excludes = [DefaultStorage.Factory::class, TestRuntimeModeController::class, TestPlayer::class],
)
internal interface QueuePlaybackDesktopTestGraph {
  @Provides
  fun runtimeModeController(): RuntimeModeController = FakeRuntimeModeController(RuntimeMode.Real)

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Provides rootScopeProvider: RootScopeProvider): QueuePlaybackDesktopTestGraph
  }
}
