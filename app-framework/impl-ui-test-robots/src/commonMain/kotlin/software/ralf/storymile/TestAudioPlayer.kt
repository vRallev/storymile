package software.ralf.storymile

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import software.ralf.storymile.player.AudioPlayer
import software.ralf.storymile.user.UserScope

/** Runs the player state machine without opening an audio device. */
@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, priority = 0)
class TestAudioPlayer : AudioPlayer() {
  override val state: StateFlow<State>
    field = MutableStateFlow(State())

  var durationMillis = 2_538_000L
  var playCount = 0
    private set

  var pauseCount = 0
    private set

  val seekRequests: List<Long>
    field = mutableListOf<Long>()
  val speedRequests: List<Float>
    field = mutableListOf<Float>()

  override fun load(source: String) {
    state.value = State(source = source, durationMillis = durationMillis, isReady = true)
  }

  override fun play() {
    playCount++
    state.update { it.copy(isPlaying = true, playWhenReady = true) }
  }

  override fun pause() {
    pauseCount++
    state.update { it.copy(isPlaying = false, playWhenReady = false) }
  }

  override fun seekTo(positionMillis: Long) {
    seekRequests += positionMillis
    advanceTo(positionMillis)
  }

  override fun setSpeed(speed: Float) {
    speedRequests += speed
  }

  override fun clear() {
    state.value = State()
  }

  override fun close() {
    clear()
  }

  fun advanceTo(positionMillis: Long) {
    state.update { it.copy(positionMillis = positionMillis) }
  }
}
