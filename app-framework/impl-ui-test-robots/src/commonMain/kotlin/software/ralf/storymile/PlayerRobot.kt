package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.Robot
import software.ralf.storymile.player.Player
import software.ralf.storymile.user.UserScope

/** Controls playback snapshots and checks commands in application UI tests. */
@ContributesRobot(UserScope::class)
class PlayerRobot(private val player: TestPlayer) : Robot {
  /** Makes [state] available to both player layouts. */
  fun publish(state: Player.State) {
    player.publish(state)
  }

  /** Checks all transport commands received since the application started. */
  fun seeCommands(toggles: Int, seeks: List<Long>, speeds: List<Float>) {
    assertThat(player.toggleCount).isEqualTo(toggles)
    assertThat(player.seekRequests).isEqualTo(seeks)
    assertThat(player.speedRequests).isEqualTo(speeds)
  }
}
