package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.Robot
import software.ralf.storymile.user.UserScope

/** Controls playback snapshots and checks commands in application UI tests. */
@ContributesRobot(UserScope::class)
class PlayerRobot(private val audio: TestAudioPlayer) : Robot {
  /** Sets the duration reported when the next source loads. */
  fun setDuration(durationMillis: Long) {
    audio.durationMillis = durationMillis
  }

  /** Simulates playback reaching [positionMillis]. */
  fun advanceTo(positionMillis: Long) {
    audio.advanceTo(positionMillis)
  }

  /** Checks all transport commands received since the application started. */
  fun seeCommands(plays: Int, pauses: Int, seeks: List<Long>, speeds: List<Float>) {
    assertThat(audio.playCount).isEqualTo(plays)
    assertThat(audio.pauseCount).isEqualTo(pauses)
    assertThat(audio.seekRequests).isEqualTo(seeks)
    assertThat(audio.speedRequests).isEqualTo(speeds)
  }
}
