package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

/** Gives tests an empty Library Queue. */
object TestQueue {
  suspend fun clear(rootScope: Scope) {
    val userScope = rootScope.metroDependencyGraph<AppBindings>().userManager.userScope.value
    val queue = userScope.metroDependencyGraph<UserBindings>().queue
    val entries = queue.episodes.filterNotNull()
    entries.first().forEach { queue.remove(it) }
    entries.first { it.isEmpty() }
  }

  @ContributesTo(AppScope::class)
  interface AppBindings {
    val userManager: UserManager
  }

  @ContributesTo(UserScope::class)
  interface UserBindings {
    val queue: QueueRepository
  }
}
