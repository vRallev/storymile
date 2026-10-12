package software.ralf.storymile

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.player.Player
import software.ralf.storymile.player.PlayerImpl
import software.ralf.storymile.user.UserScope

/** Supplies controlled audio state without opening an audio device. */
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class, replaces = [PlayerImpl::class])
class TestPlayer : Player {
  override val state: StateFlow<Player.State>
    field = MutableStateFlow(Player.State())

  /** Number of playback toggle commands received from the UI. */
  var toggleCount: Int = 0
    private set

  /** Requested seek positions in command order. */
  val seekRequests: List<Long>
    field = mutableListOf<Long>()

  /** Requested playback speeds in command order. */
  val speedRequests: List<Float>
    field = mutableListOf<Float>()

  /** Publishes an engine snapshot. Commands alone do not change the snapshot. */
  fun publish(value: Player.State) {
    state.value = value
  }

  override fun togglePlayback() {
    toggleCount++
  }

  override fun seekTo(positionMillis: Long) {
    seekRequests += positionMillis
  }

  override fun setSpeed(speed: Float) {
    speedRequests += speed
  }
}
