package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.podcast.FakePodcasts
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

/** Gives tests an empty Library Queue. */
object TestQueue {
  suspend fun clear(rootScope: Scope) {
    val graph = rootScope.metroDependencyGraph<AppBindings>()
    val userScope = graph.userManager.userScope.filterNotNull().first()
    val queue = userScope.metroDependencyGraph<UserBindings>().queue
    val entries = queue.episodes.filterNotNull()
    val seedSize =
      if (graph.runtimeModeController.mode == RuntimeMode.Fake) FakePodcasts.queue.size else 0
    entries.first { it.size >= seedSize }.forEach { queue.remove(it) }
    entries.first { it.isEmpty() }
  }

  @ContributesTo(AppScope::class)
  interface AppBindings {
    val userManager: UserManager
    val runtimeModeController: RuntimeModeController
  }

  @ContributesTo(UserScope::class)
  interface UserBindings {
    val queue: QueueRepository
  }
}
