package software.ralf.storymile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueueRepository
import software.ralf.storymile.podcast.QueuedEpisode
import software.ralf.storymile.user.UserManager
import software.ralf.storymile.user.UserScope

object TestQueue {
  suspend fun setUp(rootScope: Scope, withEpisodes: Boolean) {
    val userScope = rootScope.metroDependencyGraph<AppBindings>().userManager.userScope.value
    val queue = userScope.metroDependencyGraph<UserBindings>().queue
    clear(queue)
    if (withEpisodes) {
      val titles =
        listOf(
          "The art of slowing down",
          "The sound of a quieter life",
          "Making room for what matters",
        )
      titles.forEachIndexed { index, title -> queue.save(episode(index = index, title = title)) }
      queue.episodes.filterNotNull().first { it.size == titles.size }
    }
  }

  suspend fun clear(queue: QueueRepository) {
    val entries = queue.episodes.filterNotNull()
    entries.first().forEach { queue.remove(it) }
    entries.first { it.isEmpty() }
  }

  private fun episode(index: Int, title: String): QueuedEpisode =
    QueuedEpisode(
      feedUrl = "https://audio.test/feed.xml",
      podcastTitle = "The Quiet Current",
      artworkUrl = null,
      episode =
        PodcastEpisode(
          id = "playback-fixture-$index",
          title = title,
          description = null,
          author = null,
          artworkUrl = null,
          publishedAt = null,
          duration = 2_538.seconds,
          enclosure =
            PodcastEnclosure(
              url = "https://audio.test/$index.mp3",
              type = "audio/mpeg",
              length = null,
            ),
        ),
    )

  @ContributesTo(AppScope::class)
  interface AppBindings {
    val userManager: UserManager
  }

  @ContributesTo(UserScope::class)
  interface UserBindings {
    val queue: QueueRepository
  }
}
