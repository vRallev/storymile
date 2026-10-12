package software.ralf.storymile.player

import kotlinx.coroutines.flow.StateFlow

/** Platform audio transport. Commands run on the main dispatcher; close releases all resources. */
abstract class AudioPlayer {
  abstract val state: StateFlow<State>

  /** Replaces the source, resets position, and prepares it without starting playback. */
  abstract fun load(source: String)

  /** Starts playback, including when the current source is still preparing. */
  abstract fun play()

  abstract fun pause()

  abstract fun seekTo(positionMillis: Long)

  /** Changes speed while preserving pitch. The speed survives source changes. */
  abstract fun setSpeed(speed: Float)

  abstract fun close()

  data class State(
    val source: String? = null,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isReady: Boolean = false,
    val hasEnded: Boolean = false,
    val error: String? = null,
  )
}
