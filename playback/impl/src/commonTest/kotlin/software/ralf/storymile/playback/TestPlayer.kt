package software.ralf.storymile.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.player.Player

class TestPlayer : Player {
  override val state: StateFlow<Player.State>
    field = MutableStateFlow(Player.State())

  override fun togglePlayback() = Unit

  override fun seekTo(positionMillis: Long) = Unit

  override fun setSpeed(speed: Float) = Unit
}
