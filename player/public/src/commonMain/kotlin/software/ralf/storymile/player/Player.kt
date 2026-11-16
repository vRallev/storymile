package software.ralf.storymile.player

import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.podcast.QueuedEpisode

/** Plays the current user's Library Queue without removing completed episodes. */
interface Player {
  /** Current selection and audio state. */
  val state: StateFlow<State>

  /** Pauses or resumes playback, retries a failure, or restarts a completed queue. */
  fun togglePlayback()

  /** Starts [episode] if it is still in the queue. */
  fun play(episode: QueuedEpisode)

  /** Seeks within the current episode, clamping to its duration. */
  fun seekTo(positionMillis: Long)

  /** Changes playback speed from 0.75 to 2 times normal, preserving pitch. */
  fun setSpeed(speed: Float)

  /** A snapshot shared by all playback controls. */
  data class State(
    /** Current episode, or null when the queue is empty. */
    val episode: QueuedEpisode? = null,
    /** Episodes still to play, in their current queue order. */
    val upcomingEpisodes: List<QueuedEpisode> = emptyList(),
    /** Source position in milliseconds, independent of playback speed. */
    val positionMillis: Long = 0,
    /** Duration in milliseconds. Uses the feed's duration until the source reports one. */
    val durationMillis: Long = 0,
    /** Whether the transport is currently producing audio. */
    val isPlaying: Boolean = false,
    /** Whether audio should play when ready, including while it is preparing. */
    val playWhenReady: Boolean = false,
    /** Session-only playback speed, initially normal. */
    val speed: Float = 1f,
    /** Whether playback reached the end of the queue. */
    val isComplete: Boolean = false,
    /** Whether the current source is ready for seeking. */
    val isReady: Boolean = false,
    /** Playback failure, or null when no failure is present. */
    val error: String? = null,
  )
}
