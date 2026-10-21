package software.ralf.storymile.runtimemode

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding

/** Keeps integration tests in fake mode and ignores mode changes. */
@ContributesBinding(AppScope::class, replaces = [RuntimeModeControllerImpl::class])
class TestRuntimeModeController : RuntimeModeController {
  override val mode: RuntimeMode = RuntimeMode.Fake

  override suspend fun switchMode(mode: RuntimeMode) = Unit
}
