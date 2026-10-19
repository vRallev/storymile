package software.ralf.storymile.player

import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.user.UserScope

@ContributesBinding(UserScope::class)
class WasmJsAudioPlayer : AudioPlayer() {
  override val state: StateFlow<State>
    field = MutableStateFlow(State())

  private val transport =
    createBrowserAudioTransport {
      source,
      position,
      duration,
      playing,
      playWhenReady,
      ready,
      ended,
      error ->
      state.value =
        State(
          source = source,
          positionMillis = (position * 1_000).toLong(),
          durationMillis = (duration * 1_000).toLong(),
          isPlaying = playing,
          playWhenReady = playWhenReady,
          isReady = ready,
          hasEnded = ended,
          error = error,
        )
    }

  override fun load(source: String) {
    transport.load(source)
  }

  override fun play() {
    transport.play()
  }

  override fun pause() {
    transport.pause()
  }

  override fun seekTo(positionMillis: Long) {
    transport.seekTo(positionMillis.toDouble() / 1_000)
  }

  override fun setSpeed(speed: Float) {
    transport.setSpeed(speed.toDouble())
  }

  override fun close() {
    transport.close()
    state.value = State()
  }
}
