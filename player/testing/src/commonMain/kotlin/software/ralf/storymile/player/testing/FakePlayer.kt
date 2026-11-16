package software.ralf.storymile.player.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.player.Player
import software.ralf.storymile.podcast.QueuedEpisode

/** Records commands without changing audio state. Use [publish] to update the snapshot. */
open class FakePlayer : Player {
  final override val state: StateFlow<Player.State>
    field = MutableStateFlow(Player.State())

  var toggleCount = 0
    private set

  val playRequests: List<QueuedEpisode>
    field = mutableListOf<QueuedEpisode>()

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

  override fun play(episode: QueuedEpisode) {
    playRequests += episode
  }

  override fun seekTo(positionMillis: Long) {
    seekRequests += positionMillis
  }

  override fun setSpeed(speed: Float) {
    speedRequests += speed
  }
}
