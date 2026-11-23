package software.ralf.storymile.player.testing

import kotlin.time.Duration.Companion.seconds
import software.ralf.storymile.player.Player
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.QueuedEpisode

/** Provides a controllable playback preview for presenter and layout tests. */
open class PreviewTestPlayer : FakePlayer() {
  private var simulatePlayback = false

  fun showPreview() {
    simulatePlayback = true
    val episodes =
      listOf(
        episode(title = "The art of slowing down", durationSeconds = 2538),
        episode(title = "The sound of a quieter life", durationSeconds = 2220),
        episode(title = "Making room for what matters", durationSeconds = 2880),
        episode(title = "Small moments, big meaning", durationSeconds = 2340),
      )
    publish(
      Player.State(
        title = episodes.first().episode.title,
        episode = episodes.first(),
        upcomingEpisodes = episodes.drop(1),
        positionMillis = 754_000,
        durationMillis = 2_538_000,
        speed = 1.2f,
        isPlaying = true,
        isReady = true,
      ),
    )
  }

  fun hidePreview() {
    simulatePlayback = false
    publish(Player.State())
  }

  fun publishAudio(snapshot: Player.State) {
    simulatePlayback = false
    val selected = state.value.episode
    val title = snapshot.title ?: selected?.episode?.title
    publish(
      snapshot.copy(
        episode =
          snapshot.episode
            ?: selected?.let { it.copy(episode = it.episode.copy(title = checkNotNull(title))) },
      ),
    )
  }

  override fun togglePlayback() {
    super.togglePlayback()
    if (simulatePlayback) {
      publish(state.value.copy(isPlaying = !state.value.isPlaying))
    }
  }

  override fun seekTo(positionMillis: Long) {
    super.seekTo(positionMillis)
    if (simulatePlayback) {
      publish(
        state.value.copy(positionMillis = positionMillis.coerceIn(0, state.value.durationMillis)),
      )
    }
  }

  override fun setSpeed(speed: Float) {
    super.setSpeed(speed)
    if (simulatePlayback) {
      publish(state.value.copy(speed = speed))
    }
  }

  override fun play(episode: QueuedEpisode) {
    super.play(episode)
    if (simulatePlayback) {
      publish(
        state.value.copy(
          title = episode.episode.title,
          episode = episode,
          upcomingEpisodes = state.value.upcomingEpisodes - episode,
          positionMillis = 0,
          durationMillis = episode.episode.duration?.inWholeMilliseconds ?: 0,
          isPlaying = true,
        ),
      )
    }
  }

  private fun episode(title: String, durationSeconds: Int): QueuedEpisode =
    QueuedEpisode(
      feedUrl = "https://example.test/quiet-current.xml",
      podcastTitle = "The Quiet Current",
      artworkUrl = null,
      episode =
        PodcastEpisode(
          id = title,
          title = title,
          description = "Show notes for $title.",
          author = null,
          artworkUrl = null,
          publishedAt = null,
          duration = durationSeconds.seconds,
          enclosure =
            PodcastEnclosure(
              url = "https://example.test/audio.mp3",
              type = "audio/mpeg",
              length = null,
            ),
        ),
    )
}
