package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import software.ralf.storymile.runtimemode.FakeRuntimeModeController
import software.ralf.storymile.runtimemode.RuntimeModeController

/** Forces integration tests to use fake mode regardless of the saved mode. */
@BindingContainer
@ContributesTo(AppScope::class)
interface TestRuntimeModeBindings {
  companion object {
    @Provides fun runtimeModeController(): RuntimeModeController = FakeRuntimeModeController()
  }
}
