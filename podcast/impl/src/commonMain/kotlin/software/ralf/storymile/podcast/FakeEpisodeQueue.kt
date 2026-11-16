package software.ralf.storymile.podcast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import software.ralf.storymile.database.EpisodeQueueDatabase

/** Fake mode owns an ephemeral queue. QueueRepository serializes its mutations. */
internal class FakeEpisodeQueue : EpisodeQueueDatabase {
  private val entries =
    MutableStateFlow(
      FakePodcastAudio.queue.mapIndexed { index, episode ->
        Entry(episode = episode, position = index + 1L)
      },
    )

  override val queuedEpisodes: Flow<List<QueuedEpisode>> = entries.map { list ->
    list.map { it.episode }
  }

  override suspend fun enqueue(episode: QueuedEpisode, preferredPosition: Long?) {
    val current = entries.value
    if (current.any { it.matches(feedUrl = episode.feedUrl, episodeId = episode.episode.id) }) {
      return
    }
    val position =
      preferredPosition?.takeIf { preferred -> current.none { it.position == preferred } }
        ?: ((current.lastOrNull()?.position ?: 0L) + 1L)
    entries.value =
      (current + Entry(episode = episode, position = position)).sortedBy { it.position }
  }

  override suspend fun removeFromQueue(feedUrl: String, episodeId: String): Long? {
    val current = entries.value
    val entry =
      current.firstOrNull { it.matches(feedUrl = feedUrl, episodeId = episodeId) } ?: return null
    entries.value = current - entry
    return entry.position
  }

  override suspend fun moveInQueue(
    feedUrl: String,
    episodeId: String,
    targetFeedUrl: String,
    targetEpisodeId: String,
  ) {
    val current = entries.value
    val source =
      current.firstOrNull { it.matches(feedUrl = feedUrl, episodeId = episodeId) }?.position
        ?: return
    val target =
      current
        .firstOrNull { it.matches(feedUrl = targetFeedUrl, episodeId = targetEpisodeId) }
        ?.position ?: return
    if (source == target) {
      return
    }
    entries.value =
      current
        .map { entry ->
          val position = entry.position
          when {
            position == source -> entry.copy(position = target)
            source < target && position in source..target -> entry.copy(position = position - 1)
            target < source && position in target..source -> entry.copy(position = position + 1)
            else -> entry
          }
        }
        .sortedBy { it.position }
  }

  private data class Entry(val episode: QueuedEpisode, val position: Long) {
    fun matches(feedUrl: String, episodeId: String): Boolean =
      episode.feedUrl == feedUrl && episode.episode.id == episodeId
  }
}
