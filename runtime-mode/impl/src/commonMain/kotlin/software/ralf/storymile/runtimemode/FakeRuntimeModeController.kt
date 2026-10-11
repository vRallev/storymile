package software.ralf.storymile.runtimemode

/** Keeps test applications in fake mode without reading or writing the saved mode. */
class FakeRuntimeModeController : RuntimeModeController {
  override suspend fun mode(): RuntimeMode = RuntimeMode.Fake

  override suspend fun switchMode(mode: RuntimeMode) {
    check(mode == RuntimeMode.Fake) { "Test applications must stay in fake mode." }
  }
}
