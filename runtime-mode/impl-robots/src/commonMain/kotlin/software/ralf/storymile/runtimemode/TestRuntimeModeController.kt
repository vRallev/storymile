package software.ralf.storymile.runtimemode

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

/** Keeps integration tests in fake mode and ignores mode changes. */
@ContributesBinding(AppScope::class, replaces = [RuntimeModeControllerImpl::class])
class TestRuntimeModeController : RuntimeModeController by FakeRuntimeModeController()
