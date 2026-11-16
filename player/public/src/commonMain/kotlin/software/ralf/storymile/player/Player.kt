package software.ralf.storymile.player

import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.podcast.QueuedEpisode

/** Plays the current user's Library Queue, or the bundled episodes in Fake mode. */
interface Player {
  /** Current selection and audio state. */
  val state: StateFlow<State>

  /**
   * Pauses or resumes playback. In real mode, retries failed audio or restarts a completed queue.
   */
  fun togglePlayback()

  /** Starts [episode] if it is still in the Library Queue for this session. */
  fun play(episode: QueuedEpisode)

  /** Seeks within the current episode, clamping to its duration. */
  fun seekTo(positionMillis: Long)

  /** Changes playback speed from 0.75 to 2 times normal, preserving pitch. */
  fun setSpeed(speed: Float)

  /** A snapshot shared by all playback controls. */
  data class State(
    /** Current episode title, or null when no episode is selected. */
    val title: String? = null,
    /** Source position in milliseconds, independent of playback speed. */
    val positionMillis: Long = 0,
    /** Duration in milliseconds. Uses the feed's duration until the source reports one. */
    val durationMillis: Long = 0,
    /** Whether the transport is currently producing audio. */
    val isPlaying: Boolean = false,
    /** Session-only playback speed, initially normal. */
    val speed: Float = 1f,
    /** Whether playback reached the end of the queue. */
    val isComplete: Boolean = false,
    /** Whether the current source is ready for seeking. */
    val isReady: Boolean = false,
    /** Playback failure, or null when no failure is present. */
    val error: String? = null,
    /** Selected Library Queue entry, or null for bundled audio or an empty queue. */
    val episode: QueuedEpisode? = null,
    /** Entries still to play in the current pass, in queue order. */
    val upcomingEpisodes: List<QueuedEpisode> = emptyList(),
    /** Whether audio should play when ready, including while it is preparing. */
    val playWhenReady: Boolean = false,
  )
}
