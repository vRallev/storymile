package software.ralf.storymile.podcast

import dev.zacsweers.metro.SingleIn
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.user.UserScope

@SingleIn(UserScope::class)
@ContributesScoped(UserScope::class)
class FakeModeQueueSeeder(
  private val runtimeModeController: RuntimeModeController,
  private val queue: Lazy<QueueRepository>,
) : Scoped {
  override fun onEnterScope(scope: Scope) {
    if (runtimeModeController.mode == RuntimeMode.Fake) {
      FakePodcasts.queue.forEach { queue.value.save(it) }
    }
  }
}
