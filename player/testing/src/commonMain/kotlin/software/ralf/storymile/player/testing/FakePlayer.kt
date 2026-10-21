package software.ralf.storymile.player.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.player.Player

/** Records commands without changing audio state. Use [publish] to update the snapshot. */
open class FakePlayer : Player {
  final override val state: StateFlow<Player.State>
    field = MutableStateFlow(Player.State())

  var toggleCount = 0
    private set

  val seekRequests: List<Long>
    field = mutableListOf<Long>()

  val speedRequests: List<Float>
    field = mutableListOf<Float>()

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
