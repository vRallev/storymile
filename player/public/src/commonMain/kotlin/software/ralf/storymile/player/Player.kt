package software.ralf.storymile.player

import kotlinx.coroutines.flow.StateFlow

/** Plays the bundled fake episodes in order for the current user session. */
interface Player {
  /** Actual audio state. No episode is selected outside fake mode. */
  val state: StateFlow<State>

  /** Pauses or resumes the current episode. Does not restart a completed queue. */
  fun togglePlayback()

  /** Seeks within the current episode, clamping to its duration. */
  fun seekTo(positionMillis: Long)

  /** Changes playback speed from 0.75 to 2 times normal, preserving pitch. */
  fun setSpeed(speed: Float)

  /** A snapshot shared by all playback controls. */
  data class State(
    /** Current episode title, or null when audio playback is unavailable. */
    val title: String? = null,
    /** Source position in milliseconds, independent of playback speed. */
    val positionMillis: Long = 0,
    /** Source duration in milliseconds, or zero while preparing. */
    val durationMillis: Long = 0,
    /** Whether the transport is currently producing audio. */
    val isPlaying: Boolean = false,
    /** Session-only playback speed, initially normal. */
    val speed: Float = 1f,
    /** Whether all three episodes have finished. */
    val isComplete: Boolean = false,
    /** Whether the current source is ready for seeking. */
    val isReady: Boolean = false,
    /** Playback failure, or null when no failure is present. */
    val error: String? = null,
  )
}
