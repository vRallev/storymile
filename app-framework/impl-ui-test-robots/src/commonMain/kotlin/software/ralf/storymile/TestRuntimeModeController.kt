package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.runtimemode.RuntimeModeControllerImpl

/** Keeps integration tests in fake mode and ignores mode changes. */
@ContributesBinding(AppScope::class, replaces = [RuntimeModeControllerImpl::class])
class TestRuntimeModeController : RuntimeModeController {
  override suspend fun mode(): RuntimeMode = RuntimeMode.Fake

  override suspend fun switchMode(mode: RuntimeMode) = Unit
}
